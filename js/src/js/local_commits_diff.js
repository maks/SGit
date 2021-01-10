import $ from "jquery";
import hljs from "highlight.js/lib/highlight";
import diff from "highlight.js/lib/languages/diff";
import "../css/local_commits_diff.scss";
hljs.registerLanguage("diff", diff);

$("body").addClass("hljs-theme-rainbow");

var colorDiff = function(index) {
    var changeTypeBlock, codeBlock, codes, diffBlock, infoBlock, pathBlock;
    var diff = CodeLoader.getDiff(index);
    if (!diff) {
        return;
    }
    var changeType = CodeLoader.getChangeType(index);
    var path;
    if (changeType === "ADD") {
        path = CodeLoader.getNewPath(index);
    } else {
        path = CodeLoader.getOldPath(index);
    }
    var highlighted = hljs.highlight("diff", diff, true);
    var diffContent = highlighted.value.split("\n");
    codes = diffContent.slice(4).join("\n");
    diffBlock = $("<div>", {
        class: "diff-block"
    });
    infoBlock = $("<div>", {
        class: "info-block"
    });
    pathBlock = $("<div>", {
        class: "path-block"
    }).text(path);
    changeTypeBlock = $("<div>", {
        class: "change-type"
    }).text(changeType);
    infoBlock.append(changeTypeBlock);
    infoBlock.append(pathBlock);
    codeBlock = $("<code>").html(codes);
    codeBlock = $("<pre>", {
        class: "diff"
    }).append(codeBlock);
    diffBlock.append(infoBlock);
    diffBlock.append(codeBlock);
    $("body").append(diffBlock);
};

var commitInfo = function() {
    if (!CodeLoader.haveCommitInfo()) {
        return;
    }
    var commitMessage = CodeLoader.getCommitMessage();
    var commitInfo = CodeLoader.getCommitInfo();
    var diffBlock = $("<div>", {
        class: "diff-block"
    });
    var infoBlock = $("<div>", {
        class: "info-block"
    });
    var commitInfoBlock = $("<pre>", {
        class: "commitinfo-block"
    }).text(commitInfo);
    infoBlock.append(commitInfoBlock);
    diffBlock.append(infoBlock);
    var codeBlock = $("<code>").text(commitMessage);
    codeBlock = $("<pre>", {
        class: "diff"
    }).append(codeBlock);
    diffBlock.append(codeBlock);
    $("body").append(diffBlock);
};

window.notifyEntriesReady = function() {
    commitInfo();
    var length = CodeLoader.getDiffSize();
    var results = [];
    var ref = length - 1;
    for (var index = 0; index <= ref; index += 1) {
        results.push(colorDiff(index));
    }
    return results;
};

$(document).ready(function() {
    CodeLoader.getDiffEntries();
});
