window.coap_proxy = 'http://localhost:8080';
var status_map = {
    64: {status: 200, statusText: 'OK'},
    65: {status: 201, statusText: 'Created'},
    66: {status: 202, statusText: 'Deleted'},
    67: {status: 203, statusText: 'Valid'},
    68: {status: 204, statusText: 'Changed'},
    69: {status: 205, statusText: 'Content'},
    128: {status: 400, statusText: 'Bad Request'},
    129: {status: 401, statusText: 'Unauthorized'},
    130: {status: 402, statusText: 'Bad Option'},
    131: {status: 403, statusText: 'Forbidden'},
    132: {status: 404, statusText: 'Not Found'},
    133: {status: 405, statusText: 'Method Not Allowed'},
    141: {status: 413, statusText: 'Request Entity Too Large'},
    143: {status: 415, statusText: 'Unsupported Content-Format'},
    160: {status: 500, statusText: 'Internal Server Error'},
    161: {status: 501, statusText: 'Not Implemented'},
    162: {status: 502, statusText: 'Bad Gateway'},
    163: {status: 503, statusText: 'Service Unavailable'},
    164: {status: 504, statusText: 'Gateway Timeout'},
    165: {status: 505, statusText: 'Proxying Not Supported'}
};

function CoAPRequest(type) {
    this.method = 'GET';
    this.uri = null;
    this.async = true;
    this.onreadystatechange = function () {

    };
    this.readyState = 0;
    this.payload = '';
    this.onload = function () {

    };
    this.onerror = function () {

    };

}

CoAPRequest.prototype.open = function (method, uri, async) {
    this.method = method;
    this.uri = uri;
    this.async = async;
};

CoAPRequest.prototype.setRequestHeader = function () {

};

CoAPRequest.prototype.abort = function () {

};
CoAPRequest.prototype.getAllResponseHeaders = function () {

};

CoAPRequest.prototype.send = function (payload) {
    this.payload = payload;
    var self = this;
    var xhr = new XMLHttpRequest();
    xhr.open('POST', window.coap_proxy + '/request', this.async);
    xhr.setRequestHeader('Content-Type', 'application/json');
    var jsondata = JSON.stringify({
        'method': this.method,
        'url': this.uri,
        'payload': this.payload
    });
    xhr.onreadystatechange = function () {
        self.readyState = xhr.readyState;
        self.onreadystatechange();
    };
    function processResponse() {
        var o = JSON.parse(xhr.responseText);
        if ('error' in o){
            self.error = o.error;
        }else {
            self.error = null;
            var status = (status_map[o.code] || {status: 500, statusText: 'Unkown Status ' + o.code});
            self.status = status.status;
            self.statusText = status.statusText;
            self.code = o.code;
            self.responseText = o.payload;
        }
    }

    xhr.onload = function () {
        processResponse();
        if(self.error) {
            self.onerror();
        } else {
            self.onload();
        }
    };
    xhr.onerror = function () {
        self.onerror();
    };
    xhr.send(jsondata);
    if (!this.async) {
        processResponse();
    }

};

function setup_jquery_coap_support(){
    $.ajaxPrefilter(function (options, originalOptions, jqXHR) {
        if (options.url.indexOf('coap://') === 0) {
            options.xhr = function () {
                return new CoAPRequest();
            };
        }
    });
}
if (window.jQuery) {
    setup_jquery_coap_support()
}