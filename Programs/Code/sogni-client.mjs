import * as sogni from "@sogni-ai/sogni-client";

const names = Object.keys(sogni).sort();
console.log("Sogni client loaded");
console.log(names.length ? names.join(", ") : "(no named exports)");
if (!process.env.SOGNI_APP_ID) {
  console.log("Set SOGNI_APP_ID, SOGNI_USERNAME, and SOGNI_PASSWORD to sign in.");
}
