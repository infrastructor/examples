inlineInventory {
  node id: 'haproxy',   host: '192.168.55.10', username: 'ubuntu', keyfile: keypath('haproxy'),   tags: [role: 'haproxy']
  node id: 'rancher-1', host: '192.168.55.11', username: 'ubuntu', keyfile: keypath('rancher-1'), tags: [role: 'rancher']
  node id: 'rancher-2', host: '192.168.55.12', username: 'ubuntu', keyfile: keypath('rancher-2'), tags: [role: 'rancher']
  node id: 'mysql',     host: '192.168.55.13', username: 'ubuntu', keyfile: keypath('mysql'),     tags: [role: 'mysql']
}.provision {

  task name: 'install docker on all hosts', parallel: 4, actions: {
    shell "sudo apt update"
    shell "sudo curl -Ssl https://get.docker.com | sh"
    shell "sudo usermod -aG docker ubuntu"
  }

  def MYSQL_PASSWORD = input message: "Enter MySQL password for rancher user: ", secret: true

  task name: 'run a mysql server', filter: {'role:mysql'}, actions: {
    info "launching a mysql instance"
    shell "docker rm -f rancher-storage || true"
    shell ("docker run -p 3306:3306 " + \
           "-d -e MYSQL_RANDOM_ROOT_PASSWORD=yes " + \
           "-e MYSQL_PASSWORD=$MYSQL_PASSWORD " + \
           "-e MYSQL_USER=rancher " + \
           "-e MYSQL_DATABASE=rancher " + \
           "--name rancher-storage mysql:5.6.37")

    info "wating for mysql instance is up and running"
    retry count: 10, delay: 2000, actions: {
      assert canConnectTo {
        port = 3306
        host = node.host
      }
    }
  }

  task name: 'run a couple of rancher servers', parallel: 2, filter: {'role:rancher'}, actions: {
    shell "docker rm -f rancher-server || true"
    shell ("docker run -d -p 80:8080 " + \
           "-p 9345:9345 --restart=always " + \
           "--name=rancher-server rancher/server:v1.6.4 " + \
           "--advertise-address ${node.host} " + \
           "--advertise-http-port 80 " + \
           "--db-host 192.168.55.13 " + \
           "--db-port 3306 " + \
           "--db-pass $MYSQL_PASSWORD " + \
           "--db-user rancher " + \
           "--db-name rancher")
  }

  task name: 'run an haproxy load balancer', filter: {'role:haproxy'}, actions: {
    info "uploading haproxy configuration"
    directory sudo: true, target: '/etc/haproxy', mode: 600
    template(mode: 600, sudo: true) {
      source = 'templates/haproxy.cfg'
      target = '/etc/haproxy/haproxy.cfg'
      bindings = [
        port: 80,
        servers: [
          [name: 'rancher1', host: '192.168.55.11', port: 80],
          [name: 'rancher2', host: '192.168.55.12', port: 80]
        ]
      ]
    }

    info "launching haproxy instance"
    shell "docker run -d -v /etc/haproxy:/usr/local/etc/haproxy:ro -p 80:80 --name haproxy haproxy:1.7"

    info "waiting for haproxy is up and running"
    retry count: 10, delay: 5000, actions: {
      def response = httpGet url: "http://$node.host/ping"
      assert response.code == 200
    }
  }
}

def keypath(def machine) { ".vagrant/machines/$machine/virtualbox/private_key" }
