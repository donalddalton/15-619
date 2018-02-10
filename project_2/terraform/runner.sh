#!/usr/bin/env bash

echo yes | terraform apply -lock=false;
terraform output -json >> parameters.json;
python3 register.py;
