#!/bin/bash

export DOCKER_EMAIL=<DOCKER_EMAIL>

az login

export SUBSCRIPTION_ID=<SUBSCRIPTION_ID>

# create the group
export AZ_RESOURCE_GROUP="playground-rg"
az group create --name=${AZ_RESOURCE_GROUP} --location=eastus

# create ssh key to access cluster
ssh-keygen -t rsa -b 2048 -C ${DOCKER_EMAIL}

# create a new service principal
az ad sp create-for-rbac --role="Contributor" --scopes="/subscriptions/${SUBSCRIPTION_ID}"

export PRINCIPAL_CLIENT_ID=<PRINCIPAL_CLIENT_ID>
export PRINCIPAL_CLIENT_SECRET=<PRINCIPAL_CLIENT_SECRET>
export AZ_REGISTRY_NAME="backendplaygroundregistry"

# enable admin access
export AZ_LOGIN_SERVER="backendplaygroundregistry.azurecr.io"
export AZ_LOGIN_SERVER_PASSWORD=<AZ_LOGIN_SERVER_PASSWORD>

# verify, should see new registry
az acr list --resource-group ${AZ_RESOURCE_GROUP} --query "[].{acrLoginServer:loginServer}" --output table

# log into the registry
docker login ${AZ_LOGIN_SERVER}

# tag & push the image
docker tag <IMAGE_ID> ${AZ_LOGIN_SERVER}/backend:v1
docker push ${AZ_LOGIN_SERVER}/backend

# verify
az acr repository list --name ${AZ_REGISTRY_NAME} --output table
az provider register -n Microsoft.ContainerService

# create the cluster
export AZ_CLUSTER_NAME="backend-cluster"
az aks create --resource-group ${AZ_RESOURCE_GROUP} --name ${AZ_CLUSTER_NAME} --node-count 1 --generate-ssh-keys --kubernetes-version 1.8.1

az aks install-cli

# validate
az aks get-credentials --resource-group=${AZ_RESOURCE_GROUP} --name=${AZ_CLUSTER_NAME}

# create secret to automate pod authentication
export AZ_SECRET_NAME="secretkey"
kubectl create secret docker-registry ${AZ_SECRET_NAME} \
    --docker-server=${AZ_LOGIN_SERVER} \
    --docker-username=${AZ_REGISTRY_NAME} \
    --docker-password=${AZ_LOGIN_SERVER_PASSWORD}\
    --docker-email=${DOCKER_EMAIL}
