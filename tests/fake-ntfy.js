// Servidor mínimo no formato do ntfy.sh para os testes: POST /tópico, GET /tópico/json?poll=1&since=, GET /tópico/sse.
const http = require("http");
module.exports = function fakeNtfy() {
  const topics = {}, subs = {};
  let n = 0, down = false;
  const server = http.createServer((req, res) => {
    res.setHeader("Access-Control-Allow-Origin", "*");
    if (down) { res.writeHead(503); return res.end(); }
    const [path, qs] = req.url.split("?"), parts = path.split("/").filter(Boolean), topic = parts[0], list = (topics[topic] = topics[topic] || []);
    if (req.method === "POST") {
      let body = ""; req.on("data", (d) => (body += d));
      return req.on("end", () => {
        const ev = { id: "m" + ++n, event: "message", topic, message: body };
        list.push(ev); (subs[topic] || []).forEach((r) => r.write(`data: ${JSON.stringify(ev)}\n\n`));
        res.end(JSON.stringify(ev));
      });
    }
    const since = new URLSearchParams(qs).get("since"), at = list.findIndex((e) => e.id === since), old = since === "all" ? list : list.slice(at + 1);
    if (parts[1] === "json") return res.end(old.map((e) => JSON.stringify(e)).join("\n"));
    res.writeHead(200, { "Content-Type": "text/event-stream" });
    res.write("event: open\ndata: {}\n\n");
    old.forEach((e) => res.write(`data: ${JSON.stringify(e)}\n\n`));
    (subs[topic] = subs[topic] || []).push(res);
    req.on("close", () => (subs[topic] = subs[topic].filter((r) => r !== res)));
  });
  return { server, topics, setDown(v) { down = v; if (v) Object.values(subs).flat().forEach((r) => r.destroy()); } };
};
