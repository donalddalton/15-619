FROM ubuntu:latest

# install pip/python3/flask/requests
RUN apt-get update -y
RUN apt-get install -y python3-pip python3-dev build-essential
RUN pip3 install Flask requests

# expose the frontend container port
EXPOSE 5000

# add the service
ADD ./playground.py .

# add the html
COPY ./site ./site

CMD ["playground.py"]

ENTRYPOINT ["/usr/bin/python3"]




