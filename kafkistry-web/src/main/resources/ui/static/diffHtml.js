let dmp = new diff_match_patch();

function generateDiffHtml(before, after) {
    let diffs = generateDetailedDiff(before, after);
    let html = "";
    diffs.forEach(function (diff) {
        let type = diff[0];
        let content = diff[1];
        let detail = diff[2];
        switch (type) {
            case DIFF_EQUAL:
                html += spanText( 'diff-equal', content);
                break;
            case DIFF_DELETE:
                html += "<div class='diff-removed width-full'>";
                if (detail) {
                    detail.forEach(function (detailDiff) {
                        if (detailDiff[0] === DIFF_EQUAL) {
                            html += spanText( 'diff-removed', detailDiff[1]);
                        } else {
                            html += spanText( 'diff-removed-detail', detailDiff[1]);
                        }
                    });
                } else {
                    html += spanText( 'diff-removed', content);
                }
                html += "</div>";
                break;
            case DIFF_INSERT:
                html += "<div class='diff-added width-full'>";
                if (detail) {
                    detail.forEach(function (detailDiff) {
                        if (detailDiff[0] === DIFF_EQUAL) {
                            html += spanText( 'diff-added', detailDiff[1]);
                        } else {
                            html += spanText( 'diff-added-detail', detailDiff[1]);
                        }
                    });
                } else {
                    html += spanText( 'diff-added', content);
                }
                html += "</div>";
                break;
        }
    });
    return html;
}

function spanText(clazz, text) {
    let escapedHtmlText = $("<div>").text(text).html();
    return "<span class='" + clazz + "'>" + escapedHtmlText + "</span>";
}
function generateDetailedDiff(before, after) {
    let diffs = breakToLineDiffs(before, after);
    let prevRemove = null;
    diffs.forEach(function (diff) {
        let type = diff[0];
        switch (type) {
            case DIFF_EQUAL:
                prevRemove = null;
                break;
            case DIFF_DELETE:
                prevRemove = diff;
                break;
            case DIFF_INSERT:
                if (prevRemove) {
                    let deletedLines = prevRemove[1];
                    let insertedLines = diff[1];
                    let localDiffs = dmp.diff_main(deletedLines, insertedLines);
                    let detailDeletion = [];
                    let detailInsertion = [];
                    localDiffs.forEach(function (localDiff) {
                        let localType = localDiff[0];
                        switch (localType) {
                            case DIFF_EQUAL:
                                detailDeletion.push(localDiff);
                                detailInsertion.push(localDiff);
                                break;
                            case DIFF_DELETE:
                                detailDeletion.push(localDiff);
                                break;
                            case DIFF_INSERT:
                                detailInsertion.push(localDiff);
                                break
                        }
                    });
                    prevRemove.push(detailDeletion);
                    diff.push(detailInsertion);
                }
                prevRemove = null;
                break;
        }
    });
    return diffs;
}



function breakToLineDiffs(before, after) {
    let a = dmp.diff_linesToChars_(before, after);
    let lineText1 = a.chars1;
    let lineText2 = a.chars2;
    let lineArray = a.lineArray;
    let diffs = dmp.diff_main(lineText1, lineText2, false);
    dmp.diff_charsToLines_(diffs, lineArray);
    return diffs;
}

