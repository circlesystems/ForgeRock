// isCircleRunning.js

let _isSafari = false;
var ua = navigator.userAgent.toLowerCase();
if (ua.indexOf('safari') != -1) {
    if (ua.indexOf('chrome') > -1) {
    } else if (ua.indexOf('mac') != -1) {
        _isSafari = true;
    }
}
const _serverUrl = _isSafari ? "https://127.0.0.1:31414" : "http://127.0.0.1:31415";

async function isServiceRunning() {

    return fetch(_serverUrl, {
        method: "GET",
    }).then(async res => {
        return true;
    }).catch(err => {
        return false;
    });

};

const isRunning = await isServiceRunning();
output.value = isRunning;

