function initSelectPicker(select) {
    let picker = select.selectpicker();
    tweakSelectPickerBootstrapStyling(select);
    picker.on('loaded.bs.select', function () {
        let $el = $(this);
        let $lis = $el.data('selectpicker').selectpicker.main.elements.filter(function (item) {
            return $(item).find(".value-marker").length > 0;
        });
        $($lis).each(function () {
            let li = $(this);
            let liValue = li.find(".value-marker").attr("data-value");
            if (!liValue) return;
            let tooltip_title = $el.find(`option[value=${liValue}]`).attr("data-title");
            if (!tooltip_title) return;
            li.tooltip({
                'boundary': "window",
                'title': tooltip_title,
                'html': true,
            });
        });
    });
}

function tweakSelectPickerBootstrapStyling(selects) {
    selects.each(function () {
        //select-picker version 1.14.0-beta doesn't yet support bootstrap 5, so tweak classes a bit
        $(this.nextSibling)
            .removeClass("btn-light")
            .addClass("border");
    });
}
