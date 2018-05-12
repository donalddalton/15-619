import boto3
import json
import logging
import os
import shutil

from ffmpy import FFmpeg


logger = logging.getLogger()
logger.setLevel(logging.INFO)

s3 = boto3.resource('s3')


def img_input_handler(event, context):
    path = os.environ['LAMBDA_TASK_ROOT']
    logger.info('ROOT: {}'.format(path))
    ffmpeg = os.path.join(path, 'bin', 'ffmpeg')  # path to ffmpeg binary
    logger.info('RECORDS: {}'.format(event['Records']))
    for record in event['Records']:
        # get event record
        for message in json.loads(record['Sns']['Message'])['Records']:
            logger.info('MESSAGE: {}'.format(message))

            # name of source bucket
            source = message['s3']['bucket']['name']
            logger.info('SOURCE: {}'.format(source))

            # name of destination bucket
            dest = 'mypreviewbucket'
            logger.info('DEST: {}'.format(dest))

            # get name of new file and download it to tmp
            key = message['s3']['object']['key']
            prefix = os.path.splitext(key)[0]
            logger.info('KEY: {}'.format(key))

            download_path = os.path.join('/tmp', prefix)
            os.mkdir(download_path)
            video_path = os.path.join(download_path, key)

            logger.info('DOWNLOAD PATH: {}'.format(video_path))
            s3.meta.client.download_file(source, key, video_path)
            logger.info('CONTENTS: {}'.format(os.listdir(download_path)))

            # transform the video to images, write to tmp
            filename = os.path.join(download_path, prefix + '.gif')
            ff = FFmpeg(executable=ffmpeg,
                        inputs={video_path: None},
                        outputs={filename: ['-y', '-vf', 'fps=1/2']})

            # log the ffmpeg command
            logger.info('COMMAND: {}'.format(ff.cmd))

            ff.run()
            # check that output is being written
            logger.info('CONTENTS: {}'.format(os.listdir(download_path)))

            # upload each new image and delete from tmp
            for file in os.listdir(download_path):
                if file.endswith('.gif'):
                    upload_path = os.path.join(download_path, file)
                    s3.meta.client.upload_file(upload_path, dest, file)


            shutil.rmtree(download_path)
