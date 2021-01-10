import $ from "jquery";
import CodeMirror from "codemirror";
import CodeLoader from "./CodeLoader";
import "./codemirrorModes";
import "../css/editor.scss";

var lang = void 0;

var editor = void 0;

var displayFileContent = function() {
    var rawCodes = CodeLoader.getCode();
    $("#editor").text(rawCodes);
    var editorElm = document.getElementById("editor");
    var editorOption = {
        lineNumbers: true,
        mode: lang,
        theme: CodeLoader.getTheme(),
        matchBrackets: true,
        lineWrapping: true,
        readOnly: true
    };
    editor = CodeMirror.fromTextArea(editorElm, editorOption);
};

window.setLang = function(l) {
    lang = l;
    if (editor) {
        return editor.setOption("mode", lang);
    }
};

window.display = displayFileContent;

window.setEditable = function() {
    editor.setOption("readOnly", false);
};

window.save = function() {
    editor.setOption("readOnly", true);
    CodeLoader.save(editor.getValue());
};

window.copy_all = function() {
    value = editor.getValue();
    CodeLoader.copy_all(value);
};

$(document).ready(function() {
    CodeLoader.loadCode();
});
