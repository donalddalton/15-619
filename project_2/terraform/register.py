#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import boto3
import json

from botocore.exceptions import ClientError
from os import environ
from re import search
from requests import get


def delete_security_group(ec2_c, security_group_id):
    try:
        ec2_c.delete_security_group(GroupId=security_group_id)
        print('Security group: {} deleted'.format(security_group_id))
    except ClientError as e:
        print(e)


def delete_instance(ec2_r, instance_id):
    try:
        ec2_instance = ec2_r.Instance(instance_id)
        ec2_instance.terminate()
        ec2_instance.wait_until_terminated()
        print('Deleted instance: {}'.format(instance_id))
    except ClientError as e:
        print(e)


def delete_instances(ec2_c, ec2_r, delete_ami):
    try:
        instances_data = ec2_c.describe_instances()
        if 'Reservations' in instances_data.keys():
            for reservation in instances_data['Reservations']:
                for instance in reservation['Instances']:
                    if instance['ImageId'] == delete_ami:
                        delete_instance(ec2_r, instance['InstanceId'])

        print('Deleted all instances')
    except ClientError as e:
        print(e)


def update_asg(asg_c, asg_name, config_name, availability_zones, min_size, max_size, desired_size, cooldown):
    try:
        response = asg_c.update_auto_scaling_group(
            AutoScalingGroupName=asg_name,
            LaunchConfigurationName=config_name,
            MinSize=min_size,
            MaxSize=max_size,
            DesiredCapacity=desired_size,
            DefaultCooldown=cooldown,
            AvailabilityZones=availability_zones,
            HealthCheckType='ELB',
            HealthCheckGracePeriod=0,
            TerminationPolicies=['default'],
            NewInstancesProtectedFromScaleIn=False
        )
        return response
    except ClientError as e:
        print(e)


def delete_asg(asg_c, asg_name):
    try:
        asg_c.delete_auto_scaling_group(AutoScalingGroupName=asg_name, ForceDelete=True)
    except ClientError as e:
        print(e)


def delete_elb(elb_c, load_balancer_arn):
    try:
        elb_c.delete_load_balancer(LoadBalancerArn=load_balancer_arn)
    except ClientError as e:
        print(e)


def delete_launch_config(asg_c, config_name):
    try:
        asg_c.delete_launch_configuration(LaunchConfigurationName=config_name)
    except ClientError as e:
        print(e)


def delete_target_group(elb_c, target_group_arn):
    try:
        elb_c.delete_target_group(TargetGroupArn=target_group_arn)
    except ClientError as e:
        print(e)


def send_get_request(url, payload):
    code = 404
    while code != 200:  # send request until valid response
        try:
            response = get(url, params=payload, timeout=10)
            code = response.status_code
        except OSError:
            continue

    return response


def main():
    # get the parameters to do test registration/cleanup
    parameters = json.load(open('parameters.json'))
    ec2_client = boto3.client('ec2')
    ec2_resource = boto3.resource('ec2')
    elb = boto3.client('elbv2')

    try:
        response = ec2_client.describe_instances(Filters=[{'Name': 'image-id', 'Values': ['ami-ab3108d1']}])
    except ClientError as e:
        print(e)

    # wait until instance is up and running
    generator_id = response['Reservations'][0]['Instances'][0]['InstanceId']
    generator = ec2_resource.Instance(generator_id)
    generator.wait_until_running()
    generator.load()

    payload = {'passwd': environ['SUBMISSION_PWD'], 'andrewid': environ['ANDREWID']}
    url = 'http://{}/password'.format(generator.public_dns_name)
    send_get_request(url=url, payload=payload)
    print('Registered the load generator')

    # add tags to elb
    tags = [{'Key': 'Project', 'Value': '2.1'}]
    elb.add_tags(ResourceArns=[parameters['load_balancer_arn']['value']], Tags=tags)
    elb.add_tags(ResourceArns=[parameters['target_group_arn']['value']], Tags=tags)

    try:
        response = elb.describe_load_balancers()
    except ClientError as e:
        print(e)

    elb_dns = response['LoadBalancers'][0]['DNSName']

    payload = {'dns': elb_dns}
    url = 'http://{}/autoscaling'.format(generator.public_dns_name)
    response = send_get_request(url=url, payload=payload)
    test_id = search('test\.[0-9]*\.log', response.text).group(0)
    print('Test running at: \nhttp://{}/log?={}'.format(generator.public_dns_name, test_id))


if __name__ == '__main__':
    main()

