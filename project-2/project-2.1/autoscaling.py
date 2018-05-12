#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import boto3
from botocore.exceptions import ClientError
from time import sleep
from os import environ
from re import search
from requests import get
from random import choices
from string import ascii_uppercase, digits


class AsgElb:
    def __init__(self, elb_name, asg_name, target_group_name):
        self.ec2_client = boto3.client('ec2')
        self.ec2_resource = boto3.resource('ec2')
        self.elb = boto3.client('elbv2')
        self.asg = boto3.client('autoscaling')
        self.cloud = boto3.client('cloudwatch')
        self.availability_zones = ['us-east-1a', 'us-east-1b']
        self.subnets = ['subnet-82412dad', 'subnet-dc437697']
        self.elb_name = elb_name
        self.asg_name = asg_name
        self.target_group_name = target_group_name
        self.vpc_id = self.get_vpc_id()

    def create_target_group(self):
        try:
            response = self.elb.create_target_group(
                Name=self.target_group_name,
                Protocol='HTTP',
                Port=80,
                VpcId=self.vpc_id,
                HealthCheckProtocol='HTTP',
                HealthCheckPort='80',
                HealthCheckPath='/lookup/random',
                HealthCheckIntervalSeconds=15,
                HealthCheckTimeoutSeconds=10,
                HealthyThresholdCount=2,
                UnhealthyThresholdCount=2,
                Matcher={'HttpCode': '200'},
                TargetType='instance'
            )
            return response
        except ClientError as e:
            print(e)

    def create_load_balancer(self, security_groups):
        try:
            response = self.elb.create_load_balancer(
                Name=self.elb_name,
                Subnets=self.subnets,
                SecurityGroups=security_groups,
                Scheme='internet-facing',
                Tags=[{'Key': 'Project', 'Value': '2.1'}],
                Type='application',
                IpAddressType='ipv4'
            )
            return response
        except ClientError as e:
            print(e)

    def create_elb_listener(self, elb_arn, target_group_arn):
        try:
            response = self.elb.create_listener(
                LoadBalancerArn=elb_arn,
                Protocol='HTTP',
                Port=80,
                DefaultActions=[{'Type': 'forward', 'TargetGroupArn': target_group_arn}]
            )
            return response
        except ClientError as e:
            print(e)

    def create_asg(self, config_name, min_size, max_size, desired_size, cooldown, target_group_arn):
        try:
            response = self.asg.create_auto_scaling_group(
                AutoScalingGroupName=self.asg_name,
                LaunchConfigurationName=config_name,
                MinSize=min_size,
                MaxSize=max_size,
                DesiredCapacity=desired_size,
                DefaultCooldown=cooldown,
                AvailabilityZones=self.availability_zones,
                TargetGroupARNs=[target_group_arn],
                HealthCheckType='ELB',
                HealthCheckGracePeriod=0,
                TerminationPolicies=['default'],
                NewInstancesProtectedFromScaleIn=False,
                Tags=[
                    {
                        'ResourceType': 'auto-scaling-group',
                        'Key': 'Project',
                        'Value': '2.1',
                        'PropagateAtLaunch': True
                    },
                ]
            )
            return response
        except ClientError as e:
            print(e)

    def update_asg(self, config_name, min_size, max_size, desired_size, cooldown):
        try:
            response = self.asg.update_auto_scaling_group(
                AutoScalingGroupName=self.asg_name,
                LaunchConfigurationName=config_name,
                MinSize=min_size,
                MaxSize=max_size,
                DesiredCapacity=desired_size,
                DefaultCooldown=cooldown,
                AvailabilityZones=self.availability_zones,
                HealthCheckType='ELB',
                HealthCheckGracePeriod=0,
                TerminationPolicies=['default'],
                NewInstancesProtectedFromScaleIn=False
            )
            return response
        except ClientError as e:
            print(e)

    def create_simple_scale_policy(self, policy_name, adjustment, cooldown):
        try:
            response = self.asg.put_scaling_policy(
                AutoScalingGroupName=self.asg_name,
                PolicyName=policy_name,
                PolicyType='SimpleScaling',
                AdjustmentType='ChangeInCapacity',
                ScalingAdjustment=adjustment,
                Cooldown=cooldown
            )
            return response
        except ClientError as e:
            print(e)

    def create_cloud_watch_alarm(self, alarm_name, alarm_description, policy_arn, threshold, comparison_operator,
                                 metric, period, n_periods, n_points, statistic, namespace, dimensions):
        try:
            response = self.cloud.put_metric_alarm(
                AlarmName=alarm_name,
                AlarmDescription=alarm_description,
                ActionsEnabled=True,
                AlarmActions=[policy_arn],
                MetricName=metric,
                Namespace=namespace,
                Statistic=statistic,
                Dimensions=dimensions,
                Period=period,
                Unit='Seconds',
                EvaluationPeriods=n_periods,
                DatapointsToAlarm=n_points,
                Threshold=threshold,
                ComparisonOperator=comparison_operator
            )
            return response
        except ClientError as e:
            print(e)

    def create_launch_config(self, config_name, new_instance_ami, new_instance_type, security_groups,
                             detailed_monitoring=True):
        try:
            response = self.asg.create_launch_configuration(
                LaunchConfigurationName=config_name,
                ImageId=new_instance_ami,
                KeyName='project_1.1',
                SecurityGroups=security_groups,
                InstanceType=new_instance_type,
                InstanceMonitoring={'Enabled': detailed_monitoring}
            )
            return response
        except ClientError as e:
            print(e)

    def get_vpc_id(self):
        try:
            response = self.ec2_client.describe_vpcs()
            vpc_id = response.get('Vpcs', [{}])[0].get('VpcId', '')
            return vpc_id
        except ClientError as e:
            print(e)

    def session_cleanup(self, config_name, delete_ami, load_balancer_arn, target_group_arn, security_groups):
        # update the autoscaling policy
        self.update_asg(config_name, 0, 0, 0, 0)
        print('Updated autoscaling policy')
        # delete all instances
        try:
            instances_data = self.ec2_client.describe_instances()
            if 'Reservations' in instances_data.keys():
                for reservation in instances_data['Reservations']:
                    for instance in reservation['Instances']:
                        if instance['ImageId'] == delete_ami:
                            delete_instance(self.ec2_resource, instance['InstanceId'])

            print('Deleted all instances')

            # delete the autoscaling group
            self.asg.delete_auto_scaling_group(AutoScalingGroupName=self.asg_name, ForceDelete=True)
            print('Deleted autoscaling group')

            # delete the load balancer
            self.elb.delete_load_balancer(LoadBalancerArn=load_balancer_arn)
            print('Deleted load balancer')

            # delete the launch configuration
            self.asg.delete_launch_configuration(LaunchConfigurationName=config_name)
            print('Deleted launch configuration')

            # delete security groups
            for security_group in security_groups:
                delete_security_group(self.ec2_client, security_group)
            print('Deleted security group')

            # delete the target group
            self.elb.delete_target_group(TargetGroupArn=target_group_arn)
            print('Deleted target group')

            # NOTE: cloudwatch alarms are automatically deleted with the autoscaling group
        except ClientError as e:
            print(e)


def create_security_group(ec2_client, vpc_id, group_name, ip_permissions, description):
    try:
        response = ec2_client.create_security_group(GroupName=group_name, Description=description, VpcId=vpc_id)
        security_group_id = response['GroupId']
        print('Security group {}: {} created in vpc: {}'.format(group_name, security_group_id, vpc_id))
        ec2_client.authorize_security_group_ingress(GroupId=security_group_id, IpPermissions=ip_permissions)
        return response
    except ClientError as e:
        print(e)


def delete_security_group(ec2_client, security_group_id):
    try:
        ec2_client.delete_security_group(GroupId=security_group_id)
        print('Security group: {} deleted'.format(security_group_id))
    except ClientError as e:
        print(e)


def create_instance(ec2_resource, ami, instance_type, n, region, name, sg_names):
    try:
        response = ec2_resource.create_instances(
            ImageId=ami,
            InstanceType=instance_type,
            MaxCount=n,
            MinCount=n,
            Placement={'AvailabilityZone': region},
            SecurityGroups=sg_names,
            TagSpecifications=[{'ResourceType': 'instance', 'Tags': [
                {'Key': 'Name', 'Value': name},
                {'Key': 'Project', 'Value': '2.1'}
            ]}]
        )
        instance = response[0]  # wait for the instance to load
        instance.wait_until_running()
        instance.load()
        print('New instance {} running'.format(name))
        return instance
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
    # use a random suffix to avoid name collision
    suffix = '-' + ''.join(choices(ascii_uppercase + digits, k=10))
    elb_name = 'elb' + suffix
    asg_name = 'asg' + suffix
    target_group_name = 'target-group' + suffix
    ags_elb = AsgElb(elb_name=elb_name, asg_name=asg_name, target_group_name=target_group_name)

    # create web service security group
    service_sg_name = 'service' + suffix
    ip_permissions = [{'IpProtocol': 'tcp', 'FromPort': 80, 'ToPort': 80, 'IpRanges': [{'CidrIp': '0.0.0.0/0'}]}]
    service_sg = create_security_group(ec2_client=ags_elb.ec2_client, vpc_id=ags_elb.vpc_id, group_name=service_sg_name,
                                       ip_permissions=ip_permissions, description='service security group')

    # create load generator security group
    generator_sg_name = 'generator' + suffix
    create_security_group(ec2_client=ags_elb.ec2_client, vpc_id=ags_elb.vpc_id, group_name=generator_sg_name,
                          ip_permissions=ip_permissions, description='generator security group')

    # create the load generator
    generator_ami = 'ami-ab3108d1'
    generator_type = 'm3.medium'
    generator = create_instance(ec2_resource=ags_elb.ec2_resource, ami=generator_ami, instance_type=generator_type,
                                n=1, region='us-east-1b', name='generator', sg_names=[generator_sg_name])

    # register the load generator
    payload = {'passwd': environ['SUBMISSION_PWD'], 'andrewid': environ['ANDREWID']}
    url = 'http://{}/password'.format(generator.public_dns_name)
    send_get_request(url=url, payload=payload)
    print('Registered the load generator')

    # create launch configuration
    service_ami = 'ami-e731089d'
    service_type = 'm3.medium'
    config_name = 'launch-config' + suffix
    ags_elb.create_launch_config(config_name=config_name, new_instance_ami=service_ami, new_instance_type=service_type,
                                 security_groups=[service_sg['GroupId']], detailed_monitoring=True)

    # create target group for elb
    target_group = ags_elb.create_target_group()
    target_group_arn = target_group['TargetGroups'][0]['TargetGroupArn']

    # create elb
    load_balancer = ags_elb.create_load_balancer(security_groups=[service_sg['GroupId']])
    load_balancer_arn = load_balancer['LoadBalancers'][0]['LoadBalancerArn']

    # register target group with elb
    ags_elb.create_elb_listener(elb_arn=load_balancer_arn, target_group_arn=target_group_arn)

    # create the autoscaling group
    ags_elb.create_asg(config_name=config_name, min_size=1, max_size=4, desired_size=2, cooldown=0,
                       target_group_arn=target_group_arn)

    # define scale-in/scale-out policies
    scale_in_cpu_policy = ags_elb.create_simple_scale_policy(policy_name='scale-in-cpu', adjustment=-1, cooldown=100)
    scale_in_latency_policy = ags_elb.create_simple_scale_policy(policy_name='scale-in-latency', adjustment=-1, cooldown=60)
    scale_out_cpu_policy = ags_elb.create_simple_scale_policy(policy_name='scale-out-cpu', adjustment=1, cooldown=100)
    scale_out_latency_policy = ags_elb.create_simple_scale_policy(policy_name='scale-out-latency', adjustment=1, cooldown=0)

    # create alarms based on cpu utilization, attach to scaling policies
    dimensions = [{'Name': 'AutoScalingGroupName', 'Value': asg_name}]
    ags_elb.create_cloud_watch_alarm(alarm_name='scale-in-cpu', alarm_description='scale in on cpu', namespace='AWS/EC2',
                                     dimensions=dimensions, policy_arn=scale_in_cpu_policy['PolicyARN'],
                                     threshold=20, n_periods=1, n_points=1, metric='CPUUtilization', period=300,
                                     comparison_operator='LessThanThreshold', statistic='Average')

    ags_elb.create_cloud_watch_alarm(alarm_name='scale-out-cpu', alarm_description='scale out on cpu', namespace='AWS/EC2',
                                     policy_arn=scale_out_cpu_policy['PolicyARN'],  dimensions=dimensions, threshold=80,
                                     n_periods=1, n_points=1, metric='CPUUtilization', period=5*60,
                                     comparison_operator='GreaterThanThreshold', statistic='Average')

    # create alarms based on response time latency, attach to scaling policies
    dimensions = [{'Name': 'LoadBalancer', 'Value': search('app.*', load_balancer_arn).group(0)}]
    ags_elb.create_cloud_watch_alarm(alarm_name='scale-out-latency', alarm_description='scale out on latency',
                                     namespace='AWS/ApplicationELB', policy_arn=scale_out_latency_policy['PolicyARN'],
                                     dimensions=dimensions, threshold=1.0, n_periods=1, n_points=1, period=60,
                                     metric='TargetResponseTime', comparison_operator='GreaterThanThreshold',
                                     statistic='Maximum')

    ags_elb.create_cloud_watch_alarm(alarm_name='scale-in-latency', alarm_description='scale in on latency',
                                     namespace='AWS/ApplicationELB', policy_arn=scale_in_latency_policy['PolicyARN'],
                                     dimensions=dimensions, threshold=0.5, n_periods=3, n_points=3, period=60,
                                     metric='TargetResponseTime', comparison_operator='LessThanThreshold',
                                     statistic='Maximum')

    # start the test
    elb_dns = load_balancer['LoadBalancers'][0]['DNSName']
    payload = {'dns': elb_dns}
    url = 'http://{}/autoscaling'.format(generator.public_dns_name)
    response = send_get_request(url=url, payload=payload)
    test_id = search('test\.[0-9]*\.log', response.text).group(0)
    print('Test running at: \nhttp://{}/log?={}'.format(generator.public_dns_name, test_id))
    sleep(50*60)  # sleep for 50 minutes now that test has begun

    # cleanup the session
    ags_elb.session_cleanup(config_name=config_name, delete_ami=service_ami, load_balancer_arn=load_balancer_arn,
                            target_group_arn=target_group_arn, security_groups=[service_sg['GroupId']])


if __name__ == '__main__':
    main()

