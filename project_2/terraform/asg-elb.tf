# Terraform configuration for creating autoscaling groups, attaching load balancer, and attaching cloudwatch alarms.
locals {
  common_tags = {
    Project = "2.1"
  }
  asg_tags = [
    {
      key                 = "Project"
      value               = "2.1"
      propagate_at_launch = true
    }
  ]
}

provider "aws" {
  region = "us-east-1"
}

# load generator security group
resource "aws_security_group" "lg" {
  # HTTP access from anywhere
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = "${local.common_tags}"
}

# web service security group
resource "aws_security_group" "elb_asg" {
  # HTTP access from anywhere
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = "${local.common_tags}"
}

# load generator instance
resource "aws_instance" "web" {
  ami                = "ami-ab3108d1"
  instance_type      = "m3.medium"
  availability_zone  = "us-east-1b"
  tags               = "${local.common_tags}"
}

# load balancer configuration
resource "aws_launch_configuration" "lc" {
  name              = "launch_config"
  image_id          = "ami-e731089d"
  instance_type     = "m3.medium"
  enable_monitoring = true

  security_groups = ["${aws_security_group.elb_asg.id}"]
}

# autoscaling group
resource "aws_autoscaling_group" "asg" {
  name                 = "my-asg"
  availability_zones = ["us-east-1a", "us-east-1b"]
  max_size             = 2
  min_size             = 1
  desired_capacity     = 1
  default_cooldown     = 0
  health_check_grace_period = 10
  health_check_type    = "ELB"
  force_delete         = true
  launch_configuration = "${aws_launch_configuration.lc.name}"

  tags  = "${local.asg_tags}"
}

# load balancer
resource "aws_lb" "elb" {
  name            = "test-lb-tf"
  internal        = false
  security_groups = ["${aws_security_group.elb_asg.id}"]
  subnets         = ["subnet-82412dad", "subnet-dc437697"]

  enable_deletion_protection = false

  tags  = "${local.asg_tags}"
}

output "load_balancer_arn" {
  value = "${aws_lb.elb.arn}"
}

# load balancer target group
resource "aws_lb_target_group" "elb_tg" {
  name     = "tf-example-lb-tg"
  port     = 80
  protocol = "HTTP"
  vpc_id   = "vpc-9a8c87e2"
  tags  = "${local.asg_tags}"

  health_check = {
    path                = "/"
    port                = 80
    protocol            = "HTTP"
    interval            = 15
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 2
    matcher             = 200
  }
}

output "target_group_arn" {
  value = "${aws_lb_target_group.elb_tg.arn}"
}

# load balancer listener
resource "aws_lb_listener" "elb_listener" {
  load_balancer_arn = "${aws_lb.elb.arn}"
  port              = 80
  protocol          = "HTTP"

  default_action {
    target_group_arn = "${aws_lb_target_group.elb_tg.arn}"
    type             = "forward"
  }
}

# scale-in-cpu policy
resource "aws_autoscaling_policy" "scale_in_cpu" {
  name                   = "scale-in-cpu"
  scaling_adjustment     = -1
  adjustment_type        = "ChangeInCapacity"
  cooldown               = 300
  autoscaling_group_name = "${aws_autoscaling_group.asg.name}"
}

# scale-out-cpu policy
resource "aws_autoscaling_policy" "scale_out_cpu" {
  name                   = "scale-out-cpu"
  scaling_adjustment     = 1
  adjustment_type        = "ChangeInCapacity"
  cooldown               = 300
  autoscaling_group_name = "${aws_autoscaling_group.asg.name}"
}

# scale-in-latency policy
resource "aws_autoscaling_policy" "scale_in_latency" {
  name                   = "scale-in-latency"
  scaling_adjustment     = -1
  adjustment_type        = "ChangeInCapacity"
  cooldown               = 60
  autoscaling_group_name = "${aws_autoscaling_group.asg.name}"
}

# scale-out-latency policy
resource "aws_autoscaling_policy" "scale_out_latency" {
  name                   = "scale-out-latency"
  scaling_adjustment     = 1
  adjustment_type        = "ChangeInCapacity"
  cooldown               = 0
  autoscaling_group_name = "${aws_autoscaling_group.asg.name}"
}

# scale-in-cpu alarm
resource "aws_cloudwatch_metric_alarm" "scale_in_cpu_alarm" {
  alarm_name                = "scale-in-cpu-alarm"
  comparison_operator       = "LessThanThreshold"
  evaluation_periods        = "1"
  datapoints_to_alarm       = "1"
  metric_name               = "CPUUtilization"
  actions_enabled           = true
  alarm_actions             = ["${aws_autoscaling_policy.scale_in_cpu.arn}"]
  namespace                 = "AWS/EC2"
  period                    = "300"
  statistic                 = "Average"
  threshold                 = "20"
  alarm_description         = "This metric monitors ec2 cpu utilization"

  dimensions {
    AutoScalingGroupName = "${aws_autoscaling_group.asg.name}"
  }
}

# scale-out-cpu alarm
resource "aws_cloudwatch_metric_alarm" "scale_out_cpu_alarm" {
  alarm_name                = "scale-out-cpu-alarm"
  comparison_operator       = "GreaterThanThreshold"
  evaluation_periods        = "1"
  datapoints_to_alarm       = "1"
  metric_name               = "CPUUtilization"
  namespace                 = "AWS/EC2"
  actions_enabled           = true
  alarm_actions             = ["${aws_autoscaling_policy.scale_out_cpu.arn}"]
  period                    = "300"
  statistic                 = "Average"
  threshold                 = "80"
  alarm_description         = "This metric monitors ec2 cpu utilization"

  dimensions {
    AutoScalingGroupName = "${aws_autoscaling_group.asg.name}"
  }
}

# scale-in-latency alarm
resource "aws_cloudwatch_metric_alarm" "scale_in_latency_alarm" {
  alarm_name                = "scale-in-latency-alarm"
  comparison_operator       = "LessThanOrEqualToThreshold"
  datapoints_to_alarm       = "3"
  evaluation_periods        = "3"
  metric_name               = "TargetResponseTime"
  namespace                 = "AWS/ApplicationELB"
  actions_enabled           = true
  alarm_actions             = ["${aws_autoscaling_policy.scale_in_latency.arn}"]
  period                    = "60"
  statistic                 = "Maximum"
  threshold                 = "0.5"
  alarm_description         = "This metric monitors elb latency"

  dimensions {
    LoadBalancer            = "${aws_lb.elb.arn_suffix}"
  }
}

# scale-out-latency alarm
resource "aws_cloudwatch_metric_alarm" "scale_out_latency_alarm" {
  alarm_name                = "scale-out-latency-alarm"
  comparison_operator       = "GreaterThanOrEqualToThreshold"
  datapoints_to_alarm       = "1"
  evaluation_periods        = "1"
  metric_name               = "TargetResponseTime"
  namespace                 = "AWS/ApplicationELB"
  actions_enabled           = true
  alarm_actions             = ["${aws_autoscaling_policy.scale_out_latency.arn}"]
  period                    = "60"
  statistic                 = "Maximum"
  threshold                 = "1.0"
  alarm_description         = "This metric monitors elb latency"

  dimensions {
    LoadBalancer            = "${aws_lb.elb.arn_suffix}"
  }
}
