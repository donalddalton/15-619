FROM ubuntu:latest

RUN apt-get update -y
RUN apt-get install -y python3-pip python3-dev build-essential
RUN pip3 install Flask

EXPOSE 6000

ADD ./pyService.py .

CMD ["pyService.py"]

ENTRYPOINT ["/usr/bin/python3"]




