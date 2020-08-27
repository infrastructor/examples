def keypath(def machine) { ".vagrant/machines/$machine/virtualbox/private_key" }

def inventory = inlineInventory {
    node id: 'master', host: '192.168.65.10', username: 'vagrant', keyfile: keypath('master'), tags: [role: 'master']
    (1..2).each { 
        node id: "node-$it", host: "192.168.65.1$it", username: 'vagrant', keyfile: keypath("node-$it") 
    }
}

inventory.provision { nodes ->

    def TOKEN                = "change.me00000000000000"
    def POD_NETWORK_CIDR     = "10.50.0.0/16"
    def API_SERVER_BIND_PORT = 8080
    def MASTER_HOST          = nodes['master'].host
    def ON_ALL_NODES         = nodes.size()

    task name: "initializing hosts", parallel: ON_ALL_NODES, actions: {
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

        shell user: 'root', command: "hostname ${node.id}"
        shell user: 'root', command: "swapoff -a"
    }

    task name: "installing docker", parallel: ON_ALL_NODES, actions: { node ->
        shell user: 'root', command: '''
            dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo
            dnf install docker-ce --nobest -y
            usermod -aG docker vagrant
        '''

        file user: 'root', target: '/etc/docker/daemon.json', content: '''
        {
            "exec-opts": ["native.cgroupdriver=systemd"],
            "log-driver": "json-file",
            "log-opts": {
                "max-size": "100m"
            },
            "storage-driver": "overlay2",
            "storage-opts": [
                "overlay2.override_kernel_check=true"
            ]
        }
        '''

        shell user: 'root', command: 'systemctl enable docker'
        shell user: 'root', command: 'systemctl start docker'
    }
    
    task name: "installing kubernetes packages", parallel: ON_ALL_NODES, actions: { node ->
        file user: 'root', target: '/etc/yum.repos.d/kubernetes.repo', content: '''
        [kubernetes]
        name=Kubernetes
        baseurl=https://packages.cloud.google.com/yum/repos/kubernetes-el7-\$basearch
        enabled=1
        gpgcheck=1
        repo_gpgcheck=1
        gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
        exclude=kubelet kubeadm kubectl
        '''

        file user: 'root', target: '/etc/sysconfig/kubelet', content: "KUBELET_EXTRA_ARGS=--node-ip=$node.host"

        shell user: 'root', command: '''
            dnf upgrade -y
            dnf install -y kubelet kubeadm kubectl --disableexcludes=kubernetes
            systemctl enable kubelet.service
            ln -s $(which kubectl) /usr/bin/k
        '''
    }
    
    task name: "setting up kubernetes master", filter: {'role:master'}, actions: { node ->
        shell user: 'root', command: """
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

    task name: 'setting up calico', filter: {'role:master'}, actions: {
        upload source: 'calico/tigera-operator.yaml', target: '/tmp/calico/tigera-operator.yaml'

        template {
            source = 'calico/calico.yaml'
            target = '/tmp/calico/calico.yaml'
            bindings = [CALICO_IPV4POOL_CIDR: POD_NETWORK_CIDR]
        }

        shell command: '''
            kubectl create -f /tmp/calico/tigera-operator.yaml
            kubectl create -f /tmp/calico/calico.yaml
        '''
    }

    task name: "setting up worker nodes", filter: {!'role:master'}, parallel: ON_ALL_NODES, actions: {
        shell user: 'root', command: "kubeadm join $MASTER_HOST:$API_SERVER_BIND_PORT --token $TOKEN --discovery-token-unsafe-skip-ca-verification"
    }
}