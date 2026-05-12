/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.paramixel.core.internal.listener;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import org.paramixel.core.Result;
import org.paramixel.core.Runner;
import org.paramixel.core.Version;

/**
 * Writes a self-contained HTML end-of-run summary report to a configured file.
 *
 * <p>The generated report embeds all styles, scripts, and result data in a single HTML document. Result data is exposed
 * to the viewer as a JavaScript object with a {@code version} field and a {@code results} array containing the root
 * result. Time values are expressed as whole milliseconds.
 */
public class HtmlReportListener extends AbstractReportFileListener {

    private static final String TEMPLATE_PREFIX = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Paramixel Report</title>
            <style>
            *{margin:0;padding:0;box-sizing:border-box}
            body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f5f7;color:#1d1d1f}
            .header{background:linear-gradient(135deg,#1d1d1f 0%,#2d2d30 100%);color:#fff;padding:24px 32px}
            .header h1{font-size:22px;font-weight:600;letter-spacing:-0.3px}
            .header .version{font-size:13px;color:#86868b;margin-top:4px}
            .summary{display:flex;gap:16px;padding:20px 32px;background:#fff;border-bottom:1px solid #e5e5e7;flex-wrap:wrap}
            .summary-card{background:#f5f5f7;border-radius:12px;padding:16px 20px;min-width:160px}
            .summary-card .label{font-size:12px;color:#86868b;text-transform:uppercase;letter-spacing:0.5px}
            .summary-card .label-black{color:#1d1d1f}
            .summary-card .value{font-size:28px;font-weight:700;margin-top:4px}
            .summary-card .value.pass{color:#30d158}
            .summary-card .value.fail{color:#ff453a}
            .summary-card .value.skip{color:#ff9f0a}
            .toolbar{padding:12px 32px;background:#fff;border-bottom:1px solid #e5e5e7;display:flex;gap:10px;align-items:center;flex-wrap:wrap}
            .toolbar input{border:1px solid #d2d2d7;border-radius:8px;padding:8px 14px;font-size:14px;width:280px;outline:none}
            .toolbar input:focus{border-color:#007aff;box-shadow:0 0 0 3px rgba(0,122,255,0.15)}
            .toolbar button{background:#e8e8ed;border:none;border-radius:8px;padding:8px 14px;font-size:13px;cursor:pointer;color:#1d1d1f;font-weight:500}
            .toolbar button:hover{background:#d2d2d7}
            .toolbar .stat{font-size:13px;color:#86868b;margin-left:auto}
            .tree-container{padding:16px 32px 32px}
            .tree-node{margin-left:20px}
            .tree-node.root{margin-left:0}
            .node-header{display:flex;align-items:center;gap:8px;padding:6px 10px;border-radius:8px;cursor:pointer;transition:background 0.15s}
            .node-header:hover{background:rgba(0,0,0,0.04)}
            .toggle{width:20px;height:20px;display:flex;align-items:center;justify-content:center;font-size:12px;color:#86868b;flex-shrink:0;transition:transform 0.2s}
            .toggle.expanded{transform:rotate(90deg)}
            .toggle.leaf{visibility:hidden}
            .node-name{font-size:14px;font-weight:500}
            .badge{display:inline-flex;align-items:center;padding:2px 8px;border-radius:6px;font-size:11px;font-weight:600;letter-spacing:0.3px}
            .badge-pass{background:rgba(48,209,88,0.15);color:#248a3d}
            .badge-fail{background:rgba(255,69,58,0.15);color:#d70015}
            .badge-skip{background:rgba(255,159,10,0.15);color:#b25000}
            .kind-badge{background:#e8e8ed;color:#636366;font-size:10px;padding:2px 7px}
            .time-badge{font-size:12px;color:#86868b;font-variant-numeric:tabular-nums}
            .children{margin-left:12px;border-left:1px solid #d2d2d7;padding-left:12px}
            .children.collapsed{display:none}
            .suite-name{font-weight:600}
            </style>
            </head>
            <body>
            <div class="header">
              <h1>Paramixel Report</h1>
              <div class="version" id="version"></div>
            </div>
            <div class="summary" id="summary"></div>
            <div class="toolbar">
              <input type="text" id="search" placeholder="Filter actions by name..." autocomplete="off">
              <button onclick="expandAll()">Expand All</button>
              <button onclick="collapseAll()">Collapse All</button>
              <button onclick="expandFailures()">Expand Failures</button>
              <span class="stat" id="filterStat"></span>
            </div>
            <div class="tree-container" id="tree"></div>
            <script>
            const DATA = """;

    private static final String TEMPLATE_SUFFIX = """
            ;

            function fmt(ms) {
              if (ms < 1000) return ms + 'ms';
              var parts = [];
              var h = Math.floor(ms / 3600000); ms %= 3600000;
              var m = Math.floor(ms / 60000); ms %= 60000;
              var s = Math.floor(ms / 1000); var r = ms % 1000;
              if (h) parts.push(h + 'h');
              if (m) parts.push(m + 'm');
              parts.push(s + 's');
              if (r) parts.push(r + 'ms');
              return parts.join(' ');
            }

            function esc(s) {
              if (!s) return '';
              return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
            }

            function statusBadge(st) {
              const cls = st === 'PASS' ? 'badge-pass' : st === 'FAIL' ? 'badge-fail' : st === 'SKIP' ? 'badge-skip' : 'badge-pass';
              return '<span class="badge ' + cls + '">' + st + '</span>';
            }

            function countAll(nodes) {
              let pass=0,fail=0,skip=0;
              let totalTime=0;
              nodes.forEach(n => totalTime += n.runDuration||0);
              function walk(n) {
                if (n.status==='PASS') pass++; else if (n.status==='FAIL') fail++; else if (n.status==='SKIP') skip++;
                (n.children||[]).forEach(walk);
              }
              nodes.forEach(walk);
              return {pass,fail,skip,totalTime};
            }

            function renderSummary() {
              const s = countAll(DATA.results);
              document.getElementById('version').textContent = 'Version: ' + (DATA.version || 'N/A');
              document.getElementById('summary').innerHTML =
                '<div class="summary-card"><div class="label label-black">Actions Passed</div><div class="value pass">' + s.pass + '</div></div>' +
                '<div class="summary-card"><div class="label label-black">Actions Failed</div><div class="value fail">' + s.fail + '</div></div>' +
                '<div class="summary-card"><div class="label label-black">Actions Skipped</div><div class="value skip">' + s.skip + '</div></div>' +
                '<div class="summary-card"><div class="label">Total Time</div><div class="value">' + fmt(s.totalTime) + '</div></div>';
            }

            function matches(node, q) {
              if (!q) return true;
              if ((node.name || '').toLowerCase().includes(q)) return true;
              return (node.children||[]).some(c => matches(c, q));
            }

            let idCtr = 0;
            function ids(n) { n._id = idCtr++; (n.children||[]).forEach(ids); }

            function renderNode(node, isRoot, q) {
              if (!matches(node, q)) return '';
              const has = node.children && node.children.length > 0;
              const exp = q !== '' || isRoot;
              let ch = '';
              if (has) {
                ch = '<div class="children' + (exp ? '' : ' collapsed') + '" id="ch-' + node._id + '">';
                node.children.forEach(c => { ch += renderNode(c, false, q); });
                ch += '</div>';
              }
              return '<div class="tree-node' + (isRoot ? ' root' : '') + '" data-id="' + node._id + '">' +
                '<div class="node-header" onclick="tog(this)">' +
                '<span class="toggle ' + (exp ? 'expanded ' : '') + (has ? '' : 'leaf') + '">&#9654;</span>' +
                '<span class="node-name' + (has ? ' suite-name' : '') + '">' + esc(node.name) + '</span>' +
                statusBadge(node.status) +
                '<span class="badge kind-badge">' + esc(node.kind) + '</span>' +
                '<span class="time-badge">' + fmt(node.runDuration) + '</span>' +
                '</div>' + ch + '</div>';
            }

            function renderTree(q) {
              q = (q || '').trim().toLowerCase();
              idCtr = 0;
              DATA.results.forEach(ids);
              let h = '';
              DATA.results.forEach(r => { h += renderNode(r, true, q); });
              document.getElementById('tree').innerHTML = h;
              const vis = document.querySelectorAll('.tree-node.root').length;
              document.getElementById('filterStat').textContent = q ? 'Showing ' + vis + ' of ' + DATA.results.length + ' suites' : '';
            }

            function tog(hdr) {
              const togEl = hdr.querySelector('.toggle');
              if (togEl.classList.contains('leaf')) return;
              const par = hdr.parentElement;
              const ch = par.querySelector(':scope > .children');
              if (!ch) return;
              if (togEl.classList.contains('expanded')) {
                togEl.classList.remove('expanded'); ch.classList.add('collapsed');
              } else {
                togEl.classList.add('expanded'); ch.classList.remove('collapsed');
              }
            }

            function expandAll() {
              document.querySelectorAll('.toggle:not(.leaf)').forEach(t => t.classList.add('expanded'));
              document.querySelectorAll('.children').forEach(c => c.classList.remove('collapsed'));
            }

            function collapseAll() {
              document.querySelectorAll('.toggle:not(.leaf)').forEach(t => t.classList.remove('expanded'));
              document.querySelectorAll('.children').forEach(c => c.classList.add('collapsed'));
            }

            function expandFailures() {
              collapseAll();
              function walk(nodes) {
                nodes.forEach(n => {
                  if (n.status === 'FAIL') {
                    var el = document.querySelector('.tree-node[data-id="' + n._id + '"]');
                    while (el) {
                      var header = el.querySelector(':scope > .node-header');
                      var toggle = header ? header.querySelector('.toggle:not(.leaf)') : null;
                      var children = el.querySelector(':scope > .children');
                      if (toggle) toggle.classList.add('expanded');
                      if (children) children.classList.remove('collapsed');
                      el = el.parentElement ? el.parentElement.closest('.tree-node') : null;
                    }
                  }
                  if (n.children) walk(n.children);
                });
              }
              walk(DATA.results);
            }

            let debounce;
            document.getElementById('search').addEventListener('input', function() {
              clearTimeout(debounce);
              debounce = setTimeout(() => renderTree(document.getElementById('search').value), 200);
            });

            renderSummary();
            renderTree('');
            </script>
            </body>
            </html>
            """;

    /**
     * Creates an HTML report listener for the supplied file.
     *
     * @param reportFile the file that will contain the generated report
     */
    public HtmlReportListener(final String reportFile) {
        super(reportFile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeReport(Writer writer, Runner runner, Result result) throws IOException {
        writer.write(TEMPLATE_PREFIX);
        writer.write(" ");
        writeReportData(writer, result);
        writer.write(TEMPLATE_SUFFIX);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String formatName() {
        return "HTML";
    }

    private void writeReportData(Writer writer, Result result) throws IOException {
        writer.write("{\n");
        writer.write("  \"version\": \"");
        writer.write(Listeners.escapeJson(Version.getVersion()));
        writer.write("\",\n");
        writer.write("  \"results\": [\n");
        writeResult(writer, result, 2);
        writer.write("\n  ]\n");
        writer.write("}");
    }

    private void writeResult(Writer writer, Result result, int indent) throws IOException {
        String pad = "  ".repeat(indent);
        String padInner = pad + "  ";

        writer.write(pad + "{\n");

        var action = result.getAction();
        writer.write(padInner + "\"name\": \"");
        writer.write(Listeners.escapeJson(action.getName()));
        writer.write("\",\n");

        writer.write(padInner + "\"kind\": \"");
        writer.write(Listeners.escapeJson(Listeners.formatKind(action)));
        writer.write("\",\n");

        var status = result.getStatus();
        writer.write(padInner + "\"status\": \"");
        writer.write(Listeners.formatStatus(status));
        writer.write("\",\n");

        writer.write(padInner + "\"runDuration\": ");
        writer.write(String.valueOf(result.getRunDuration().toMillis()));
        writer.write(",\n");

        writer.write(padInner + "\"message\": ");
        writeNullableString(writer, status.getMessage().orElse(null));
        writer.write(",\n");

        writer.write(padInner + "\"exception\": ");
        writeNullableString(writer, Listeners.formatException(status));
        writer.write(",\n");

        List<Result> children = result.getChildren();
        writer.write(padInner + "\"children\": [");

        if (!children.isEmpty()) {
            writer.write("\n");
            for (int i = 0; i < children.size(); i++) {
                writeResult(writer, children.get(i), indent + 2);
                if (i < children.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            writer.write(padInner + "]");
        } else {
            writer.write("]");
        }

        writer.write("\n");
        writer.write(pad + "}");
    }

    private void writeNullableString(Writer writer, String value) throws IOException {
        if (value == null) {
            writer.write("null");
        } else {
            writer.write("\"");
            writer.write(Listeners.escapeJson(value));
            writer.write("\"");
        }
    }
}
