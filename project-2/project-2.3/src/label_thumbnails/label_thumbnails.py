import logging
import json
import os
import boto3


logger = logging.getLogger()
logger.setLevel(logging.INFO)

rek_client = boto3.client('rekognition')
endpoint = "http://search-label-index-2wf2swawtjptgekjwrb26udr24.us-east-1.cloudsearch.amazonaws.com"
csd_client = boto3.client('cloudsearchdomain', endpoint_url=endpoint)


def label_input_handler(event, context):
    logger.info('EVENT {}'.format(event))
    logger.info('RECORD {}'.format(event["Records"]))
    # iterate on event records
    for record in event['Records']:
        # get even source
        source = record['s3']['bucket']['name']
        logger.info('SOURCE: {}'.format(source))

        # get name of added object
        key = record['s3']['object']['key']
        logger.info('KEY: {}'.format(key))

        # call the recognition service
        response = rek_client.detect_labels(
            Image={
                'S3Object': {
                    'Bucket': source,
                    'Name': key
                }
            }
        )

        logger.info('RESPONSE: {}'.format(response))

        # extract the labels
        labels = []
        if "Labels" in response.keys():
            for label in response["Labels"]:
                labels += [label["Name"]]

        logger.info('LABELS: {}'.format(labels))

        # format doc for cloudsearch upload
        document = [{
            "fields": {
                "key": key,
                "labels": labels
            },
            "type": "add",
            "id": key
        }]

        # encode the doc
        document = json.dumps(document).encode()
        logger.info('DOCUMENT: {}'.format(document))

        # upload doc to cloudsearch
        response = csd_client.upload_documents(
            documents=document,
            contentType='application/json'
        )

        logger.info('RESPONSE: {}'.format(response))

