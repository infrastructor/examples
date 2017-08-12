# Rancher Server Deployment Example

This example demonstrates how to run a Rancher Server in HA mode on two Vagrant nodes including load balancer and database setup.

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
From the same directory execute provisioning script with infrastructor CLI:
```
infrastructor run -f provision.groovy
```
you will be asked for a MySQL password during execution.

### Step 3: check the result
Open a web browser and  go to http://192.168.55.10. You should see a rancher server welcome page.

