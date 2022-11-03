function initSelectPicker(select) {
    let picker = select.selectpicker();
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