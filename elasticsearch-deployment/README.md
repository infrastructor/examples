# ElasticSearch Cluster Deployment Example

This is a basic example of the ElasticSearch cluster deployment with Infrastructor.

To run the example make sure you have the following components installed on your machine:
* VirtualBox
* Vagrant
* Oracle Java 8 (JDK or JRE)
* Infrastructor

### Step 1: run virtual nodes
Run virtual node using vagrant CLI from the directory where the Vagrantfile is located:
```
vagrant up
```

### Step 2: run the provisioning script:
```
infrastructor run -f provision.groovy
```

### Step 3: check the result:
```
curl -XGET http://elastic:changeme@192.168.55.11:9200/_cluster/health?pretty
```

You should see the cluster health status information like this:
```json
{
  "cluster_name" : "docker-cluster",
  "status" : "green",
  "timed_out" : false,
  "number_of_nodes" : 4,
  "number_of_data_nodes" : 4,
  "active_primary_shards" : 4,
  "active_shards" : 8,
  "relocating_shards" : 0,
  "initializing_shards" : 0,
  "unassigned_shards" : 0,
  "delayed_unassigned_shards" : 0,
  "number_of_pending_tasks" : 0,
  "number_of_in_flight_fetch" : 0,
  "task_max_waiting_in_queue_millis" : 0,
  "active_shards_percent_as_number" : 100.0
}
```