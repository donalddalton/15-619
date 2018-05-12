import json
import os

from flask import Flask, request
from subprocess import Popen, STDOUT, PIPE


app = Flask(__name__, static_url_path='')

@app.route("/py/eval", methods=['GET', 'POST'])
def handle():
    results = {"stderr": '', "stdout": ''}
    if request.method == 'POST':
        # extract code based on content-type
        if request.headers['Content-Type'].endswith("json"):
            code = request.get_json()["code"].strip()
        else:
            code = request.form["code"]
        # case in which empty string is given
        if len(code) > 0:
            # run code as subprocess
            proc = Popen(["/usr/bin/python3", "-c", code], stdin=PIPE, stdout=PIPE, stderr=PIPE, close_fds=True)
            output, errors = proc.communicate()
            if len(output) > 0:
                results["stdout"] = output.decode()
            if len(errors) > 0:
                results["stderr"] = errors.decode()

    return app.response_class(response = json.dumps(results), status = 200, mimetype='application/json')


if __name__ == '__main__':
    # set the encoding scheme
    os.environ["PYTHONIOENCODING"] = "utf-8"
    app.run(threaded=True, debug=True, host="0.0.0.0", port=6000)

