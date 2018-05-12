#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import boto3
import requests
import os
import time

from botocore.exceptions import ClientError
from argparse import ArgumentParser
from subprocess import Popen, STDOUT, PIPE


def create_target_group(elb_client, target_group_name, vpc_id):
    try:
        response = elb_client.create_target_group(
            Name=target_group_name,
            Protocol='TCP',
            Port=80,
            VpcId=vpc_id,
            HealthCheckProtocol='HTTP',
            HealthCheckPort='80',
            HealthCheckPath='/',
            HealthCheckIntervalSeconds=10,
            HealthyThresholdCount=2,
            UnhealthyThresholdCount=2,
            TargetType='instance'
        )
        return response
    except ClientError as e:
        print(e)


def create_load_balancer(elb_client, elb_name, subnets):
    try:
        response = elb_client.create_load_balancer(
            Name=elb_name,
            Subnets=subnets,
            Scheme='internet-facing',
            Tags=[
                {
                    'Key': 'Project',
                    'Value': 'Phase3',
                    'Key': 'teambackend',
                    'Value': 'mysql',
                }
            ],
            Type='network',
            IpAddressType='ipv4'
        )
        print('Load balancer {} created.'.format(elb_name))
        return response
    except ClientError as e:
        print(e)


def create_elb_listener(elb_client, elb_arn, target_group_arn):
    try:
        response = elb_client.create_listener(
            LoadBalancerArn=elb_arn,
            Protocol='TCP',
            Port=80,
            DefaultActions=[
                {
                    'Type': 'forward',
                    'TargetGroupArn': target_group_arn
                }
            ]
        )
        return response
    except ClientError as e:
        print(e)


def register_targets(elb_client, target_group_arn, instance_id, port):
    try:
        response = elb_client.register_targets(
            TargetGroupArn=target_group_arn,
            Targets=[
                {
                    'Id': instance_id,
                    'Port': port
                },
            ]
        )
        print('Registered instance: {} with load balancer'.format(instance_id))
        return response
    except ClientError as e:
        print(e)


def get_vpc_id(ec2_client):
    try:
        response = ec2_client.describe_vpcs()
        vpc_id = response.get('Vpcs', [{}])[0].get('VpcId', '')
        return vpc_id
    except ClientError as e:
        print(e)


def create_security_group(ec2_client, vpc_id, group_name, ip_permissions, description):
    try:
        security_groups = ec2_client.describe_security_groups()['SecurityGroups']
        for sg in security_groups:
            if sg['GroupName'] == group_name:
                print('Security group {} already exists'.format(group_name))
                return

        response = ec2_client.create_security_group(GroupName=group_name, Description=description, VpcId=vpc_id)
        security_group_id = response['GroupId']
        print('Security group {}: {} created in vpc: {}'.format(group_name, security_group_id, vpc_id))
        ec2_client.authorize_security_group_ingress(GroupId=security_group_id, IpPermissions=ip_permissions)
        return response
    except ClientError as e:
        print(e)


def create_instance(ec2_resource, ami, instance_type, n, region, name, sg_names, user_data):
    try:
        response = ec2_resource.create_instances(
            ImageId=ami,
            InstanceType=instance_type,
            MaxCount=n,
            MinCount=n,
            Placement={'AvailabilityZone': region},
            SecurityGroups=sg_names,
            TagSpecifications=[
                {
                    'ResourceType': 'instance',
                    'Tags': [
                        {'Key': 'Name', 'Value': name},
                        {'Key': 'Project', 'Value': 'Phase3'},
                        {'Key': 'teambackend', 'Value': 'mysql'}
                    ]
                 }
            ],
            UserData=user_data,
            KeyName='shared',
            BlockDeviceMappings=[{"DeviceName": "/dev/sda1", "Ebs": {"VolumeSize": 100}}]
        )
        instance = response[0]  # wait for the instance to load
        instance.wait_until_running()
        instance.load()
        print('New instance {} running at {}'.format(name, instance.public_dns_name))
        return instance
    except ClientError as e:
        print(e)


def session_cleanup(ec2_client, ec2_resource, elb_client, instances, load_balancer_arn, target_group_arn, security_groups):
    # delete all instances
    try:
        for instance in instances:
            delete_instance(ec2_resource, instance)

        # delete the load balancer
        delete_elb(elb_client=elb_client, load_balancer_arn=load_balancer_arn)

        # delete the target group
        delete_target_group(elb_client=elb_client, target_group_arn=target_group_arn)

        # delete security groups
        for security_group in security_groups:
            delete_security_group(ec2_client=ec2_client, security_group_name=security_group)

        print('Deleted security group')
    except ClientError as e:
        print(e)


def delete_instance(ec2_resource, instance_id):
    try:
        ec2_instance = ec2_resource.Instance(instance_id)
        ec2_instance.terminate()
        ec2_instance.wait_until_terminated()
        print('Deleted instance: {}'.format(instance_id))
    except ClientError as e:
        print(e)


def delete_elb(elb_client, load_balancer_arn):
    try:
        elb_client.delete_load_balancer(LoadBalancerArn=load_balancer_arn)
        print('Deleted elb')
    except ClientError as e:
        print(e)


def delete_target_group(elb_client, target_group_arn):
    try:
        elb_client.delete_target_group(TargetGroupArn=target_group_arn)
        print('Deleted target group')
    except ClientError as e:
        print(e)


def delete_security_group(ec2_client, security_group_name):
    try:
        ec2_client.delete_security_group(GroupName=security_group_name)
        print('Security group: {} deleted'.format(security_group_name))
    except ClientError as e:
        print(e)


def main():
    ap = ArgumentParser()

    ap.add_argument(
        '-n',
        '--number',
        action='store',
        dest='n',
        type=int,
        default=2
    )

    args = ap.parse_args()

    # create the client/resource handles
    ec2_client = boto3.client('ec2')
    ec2_resource = boto3.resource('ec2')
    elb_client = boto3.client('elbv2')

    # create frontend security group
    ip_permissions = [
        {
            'IpProtocol': 'tcp',
            'FromPort': 80,
            'ToPort': 80, 'IpRanges': [{'CidrIp': '0.0.0.0/0'}]
        },
        {
            'IpProtocol': 'tcp',
            'FromPort': 22,
            'ToPort': 22, 'IpRanges': [{'CidrIp': '0.0.0.0/0'}]
        },
        {
            'IpProtocol': 'tcp',
            'FromPort': 3306,
            'ToPort': 3306, 'IpRanges': [{'CidrIp': '0.0.0.0/0'}]
        }
    ]

    vpc_id = get_vpc_id(ec2_client)
    service_sg_name = 'q3'
    create_security_group(ec2_client=ec2_client,
                          vpc_id=vpc_id,
                          group_name=service_sg_name,
                          ip_permissions=ip_permissions,
                          description='service security group')

    # get availability zone/subnet info
    response = ec2_client.describe_subnets()
    available_subnets, available_zones = [], []
    for subnet in response['Subnets']:
        zone = subnet['AvailabilityZone']
        # use these two as defaults since they both support m5.large
        if zone == 'us-east-1a' or zone == 'us-east-1b':
            assert subnet['State'] == 'available', 'zone unavailable'
            available_subnets += [subnet['SubnetId']]
            available_zones += [zone]

    # create launch configuration
    service_ami = 'ami-43a15f3e'  # ubuntu ami
    service_type = 'm5.large'
    n_instances = args.n
    availability_zone = available_zones[0]

    instances = []
    for idx in range(n_instances):
        instance = create_instance(ec2_resource=ec2_resource,
                                   ami=service_ami,
                                   instance_type=service_type,
                                   n=1,
                                   region=availability_zone,
                                   name='server-' + str(idx),
                                   user_data='',
                                   sg_names=[service_sg_name])
        instances += [instance.id]

    # create target group for elb
    target_group_name = 'target'
    target_group = create_target_group(elb_client=elb_client, target_group_name=target_group_name, vpc_id=vpc_id)
    target_group_arn = target_group['TargetGroups'][0]['TargetGroupArn']

    # create elb
    elb_name = 'load-balancer'
    load_balancer = create_load_balancer(elb_client=elb_client, elb_name=elb_name, subnets=available_subnets)
    load_balancer_arn = load_balancer['LoadBalancers'][0]['LoadBalancerArn']

    # register target group with elb
    create_elb_listener(elb_client=elb_client, elb_arn=load_balancer_arn, target_group_arn=target_group_arn)

    # register the instances with the elb
    for instance in instances:
        register_targets(elb_client=elb_client, target_group_arn=target_group_arn, instance_id=instance, port=80)

    elb = elb_client.describe_load_balancers(Names=[elb_name])['LoadBalancers'][0]
    url = 'http://{}/'.format(elb['DNSName'])
    status_code = 404
    print('Waiting for load balancer to start service...')
    while status_code != 200:  # send request until valid response
        time.sleep(1)
        try:
            _response = requests.get(url=url)
            status_code = _response.status_code
        except OSError:
            continue
    print('Load balancer is ready at {}'.format(url))

    # cleanup the session
    input("Press enter to teardown")
    session_cleanup(ec2_client=ec2_client,
                    ec2_resource=ec2_resource,
                    elb_client=elb_client,
                    instances=instances,
                    load_balancer_arn=load_balancer_arn,
                    target_group_arn=target_group_arn,
                    security_groups=[service_sg_name])


if __name__ == '__main__':
    main()
