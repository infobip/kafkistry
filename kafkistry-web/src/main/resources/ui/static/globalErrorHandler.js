let seenErrors = {};

window.onerror = function (message, source, lineno, colno, error) {
    if (source.indexOf("/DMMenu/navbar.js") > 0) {
        return;
    }
    //fingers crossed that no one will see this
    let msg = "Uncaught error occurred\n" +
        "Message: " + message + "\n" +
        (source ? "Source: " + source + "\n" : "") +
        (lineno || colno ? "Line: " + lineno + "\n" + "Column:" + colno + "\n" : "") +
        (error ? "Error: " + error + "\n" : "") +
        "\n" +
        "Please report this with steps how to reproduce, as well as any stack traces from browser's console log";
    let errContext = seenErrors[msg];
    let prevTime = 0;
    if (errContext) {
        errContext.repeat++;
        prevTime = errContext.lastShownTime;
    } else {
        errContext = {
            repeat: 1,
            lastShownTime: Date.now()
        };
        seenErrors[msg] = errContext;
    }
    if (errContext.repeat > 1) {
        msg = "(" + errContext.repeat + ") " + msg;
    }
    if (Date.now() - prevTime > 5000) {
        alert(msg);
        errContext.lastShownTime = Date.now();
    }
};
