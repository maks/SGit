import fs from "fs";

// TODO: should we load all addons and key bindings too like the old version from compress.html did?

let modes = fs.readdirSync("node_modules/codemirror/mode");
let s = "";

modes.forEach(mode => {
    if (mode != "meta.js") {
        s += `import "codemirror/mode/${mode}/${mode}";\n`;
    }
});

fs.writeFileSync("src/js/codemirrorModes.js", s);
