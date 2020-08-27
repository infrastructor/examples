# ---------------------------------------------#
# This script helps to rebuild the environment # 
# ---------------------------------------------#

# get number of VMs
VMS=$(vagrant status | grep virtualbox | cut -d ' ' -f1)
COUNT=$(echo "$VMS" | wc -l)

# destroy all VMs and update if needed
printf "$VMS" | xargs -P $COUNT -I {} vagrant destroy -f {}
vagrant box update

# launch VMs in parallel
printf "$VMS" | xargs -P $COUNT -I {} vagrant up {}

# run the provisioning script
infrastructor run -f provision.groovy
