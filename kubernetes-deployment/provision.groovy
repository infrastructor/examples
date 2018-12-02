def keypath(def machine) { ".vagrant/machines/$machine/virtualbox/private_key" }

def inventory = inlineInventory {
    node id: 'master', host: '192.168.65.10', username: 'vagrant', keyfile: keypath('master'), tags: [role: 'master']
    (1..2).each { 
        node id: "node-$it", host: "192.168.65.1$it", username: 'vagrant', keyfile: keypath("node-$it")
    }
}

inventory.provision { nodes ->

    def TOKEN = "change.me00000000000000"
    def POD_NETWORK_CIDR = "10.50.0.0/16"
    def API_SERVER_BIND_PORT = 8080
    def MASTER_HOST = nodes['master'].host
    def NODES_COUNT = nodes.size()

    task name: "initializing host", parallel: NODES_COUNT, actions: {
        file {
            user    = 'root'
            target  = "/etc/hostname"
            content = node.id
        }
                        
        insertBlock {
            user     = 'root'
            target   = '/etc/hosts'
            block    = "127.0.0.1\t${node.id}\n"
            position = START
        }
                        
        shell "sudo hostname ${node.id}"
        shell "sudo swapoff -a"
    }

    task name: 'installing docker', parallel: NODES_COUNT, actions: {
        shell user: 'root', command: '''
            apt update
            apt install docker.io -y
            usermod -aG docker vagrant
        '''
    }

    task name: "installing kubernetes", parallel: NODES_COUNT, actions: { node ->
        file {
            user = 'root'
            target = '/etc/apt/sources.list.d/kubernetes.list'
            content = 'deb http://apt.kubernetes.io/ kubernetes-xenial main'
        }

        shell user: 'root', command: '''
            curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
            apt update
            apt install kubelet kubeadm kubectl kubernetes-cni -y
        '''

        file  user: 'root', target: '/etc/default/kubelet', content: "KUBELET_EXTRA_ARGS=--node-ip=$node.host"
    }
    
    task name: "initializing kubernetes master", filter: {'role:master'}, actions: { node ->
        shell """
            sudo kubeadm init --token $TOKEN \
            --apiserver-advertise-address $node.host \
            --apiserver-bind-port $API_SERVER_BIND_PORT \
            --pod-network-cidr $POD_NETWORK_CIDR
        """

        shell '''
            mkdir -p $HOME/.kube
            sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
            sudo chown $(id -u):$(id -g) $HOME/.kube/config
        '''
    }

    task name: 'initializing calico cni', filter: {'role:master'}, actions: {
        upload   source: 'templates/calico/etcd.yaml',   target: '/tmp/etcd.yaml'
        upload   source: 'templates/calico/rbac.yaml',   target: '/tmp/rbac.yaml'
        template source: 'templates/calico/calico.yaml', target: '/tmp/calico.yaml', bindings: [
            CALICO_IPV4POOL_CIDR: POD_NETWORK_CIDR
        ]

        shell '''
            kubectl apply -f /tmp/etcd.yaml
            kubectl apply -f /tmp/rbac.yaml
            kubectl apply -f /tmp/calico.yaml
        '''
    }

    task name: "join nodes", filter: {!'role:master'}, parallel: NODES_COUNT, actions: {
        shell "sudo kubeadm join $MASTER_HOST:$API_SERVER_BIND_PORT --token $TOKEN --discovery-token-unsafe-skip-ca-verification"
    }
}