# Kubernetes Cluster Deployment Example
In this example we will run a small Kubernetes cluster: 1 master node and 2 worker nodes.

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

### Step 2: run the provisioning script
From the same directory execute provisioning script with infrastructor CLI:
```
infrastructor run -f provision.groovy
```

### Step 3: check the result
Jump to the master node by SSH:
```
vagrant ssh master
```
Then check if the Kubernetes is up and running by listing all available component:
```
kubectl get po --all-namespaces
```