import $ from "jquery";
import { env } from "process";

let CodeLoader;
if (process.env.NODE_ENV == "production") {
    CodeLoader = window.CodeLoader;
} else {
    let text;
    CodeLoader = {};
    CodeLoader.loadCode = () => {
        let fileInput = $(
            '<input type="file" style="display: none;" id="file"/>'
        );
        fileInput.change(() => {
            if (fileInput[0].files.length == 1) {
                let fileReader = new FileReader();
                fileReader.addEventListener("load", () => {
                    text = fileReader.result;
                    window.display();
                    window.setEditable();
                });
                fileReader.readAsText(fileInput[0].files[0]);
            }
        });
        $(document.body)
            .append(fileInput)
            .prepend(
                $(
                    '<button type="button">\ud83d\udcc2</button><br />'
                ).click(() => fileInput.click())
            );
    };
    CodeLoader.getCode = () => text;
    CodeLoader.getTheme = () => "midnight";
    CodeLoader.save = () => {};
    CodeLoader.copy_all = () => {};
}
export default CodeLoader;
