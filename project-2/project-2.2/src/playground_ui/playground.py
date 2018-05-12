import json
import os
import re

from flask import Flask, request, redirect
from requests import post

pythonServiceHostName = "http://<ip>";

app = Flask(__name__, static_folder='site', static_url_path='')

@app.route("/", methods=['GET'])
def handle():
    return app.send_static_file("index.html")

@app.route("/python", methods=['GET', 'POST'])
def handlePython():
    if request.method == 'POST':
        results = {"stderr": '', "stdout": '', 'map': None}
        if request.headers['Content-Type'].endswith("json"):
            code = request.get_json()["code"].strip()
        else:
            code = request.form['code']
        print(code)

        if not os.path.isfile(log):
            log_map = log_template
        else:
            log_map = json.load(open(log))
            if log_map['pid'] != log_template['pid']:  # then process has changed
                log_map = log_template
        
        # switch provider
        log_map['curr_csp'] = csp[log_map['curr_csp']]

        # check if service has dropped more than 10 requests, switch if true
        if log_map['csp'][log_map['curr_csp']]['drops'] > 10:
            log_map['curr_csp'] = csp[log_map['curr_csp']]

        dns = log_map['csp'][log_map['curr_csp']]['dns']
        response = post(dns, data = {'code': code})

        # update the log
        if response_code.match(str(response.status_code)) is None:
            log_map['csp'][curr_csp]['drops'] += 1
        
				# write the log
        with open(log, 'wt') as f:
            json.dump(log_map, f)
        
        return app.response_class(response = json.dumps(response.json()), status = 200, mimetype='application/json')
    else:
        return app.send_static_file("python.html")


log_template = {
    'pid': os.getpid(),
    'curr_csp': 'GCP',
    'csp': {
        'GCP': {'drops': 0, 'dns': 'http://backend-service.default.svc.cluster.local/py/eval'},
        'MAZ': {'drops': 0, 'dns': 'http://13.92.175.64/py/eval'}
    },
}

csp = {'GCP': 'MAZ', 'MAZ': 'GCP'}
log = os.path.abspath(os.path.join(os.path.dirname(__file__), "log.json"))
response_code = re.compile('^2[0-9]{2,2}$')


if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=5000, threaded=True)
