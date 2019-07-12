def keypath(def machine) { ".vagrant/machines/$machine/virtualbox/private_key" }

inlineInventory {
  node id: 'elastic-1', host: '192.168.55.11', username: 'vagrant', keyfile: keypath('elastic-1')
  node id: 'elastic-2', host: '192.168.55.12', username: 'vagrant', keyfile: keypath('elastic-2')
  node id: 'elastic-3', host: '192.168.55.13', username: 'vagrant', keyfile: keypath('elastic-3')
  node id: 'elastic-4', host: '192.168.55.14', username: 'vagrant', keyfile: keypath('elastic-4')
}.provision {

    task name: "install docker", parallel: 4, actions: {
      shell user: 'root', command: '''
        apt update
        apt install docker.io -y
        usermod -aG docker ubuntu
        sysctl -w vm.max_map_count=262144
      '''
    }

    task name: "run elasticsearch nodes", actions: {
      shell user: 'root', command: """
        docker run -d -p 9200:9200 -p 9300:9300 \
        -e "node.name=${node.id}" \
        -e "node.master=true" \
        -e "network.publish_host=${node.host}" \
        -e "discovery.zen.ping.unicast.hosts=${inventory.nodes*.value.host.join(',')}" \
        -e "ES_JAVA_OPTS=-Xmx1024m -Xms1024m" \
        docker.elastic.co/elasticsearch/elasticsearch:5.6.3
      """
    }
}