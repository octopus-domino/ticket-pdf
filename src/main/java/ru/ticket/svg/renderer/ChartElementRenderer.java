package ru.ticket.svg.renderer;

import ru.ticket.dto.ChartElement;
import ru.ticket.svg.SvgContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ChartElementRenderer implements ElementRenderer<ChartElement> {

    @Override
    public Class<ChartElement> supports() { return ChartElement.class; }

    @Override
    public void render(ChartElement c, SvgContext ctx) {
        StringBuilder out = ctx.body();
        String tr = c.rotation != 0
                ? String.format(Locale.ROOT, " transform=\"rotate(%.2f %.2f %.2f)\"", c.rotation, c.x, c.y)
                : "";
        out.append("<g").append(tr).append(">\n");

        List<Double> values = c.values;
        if (values == null || values.isEmpty()) {
            out.append(String.format(Locale.ROOT, "<text x=\"%.2f\" y=\"%.2f\" fill=\"#94a3b8\" font-size=\"14\" text-anchor=\"middle\">нет данных</text>\n", c.x + c.width / 2, c.y + c.height / 2));
            out.append("</g>\n");
            return;
        }

        if ("bar".equals(c.chartKind)) {
            renderBar(c, out, values);
        } else {
            renderDonut(c, out, values);
        }

        out.append("</g>\n");
    }

    private void renderBar(ChartElement c, StringBuilder out, List<Double> values) {
        double max = values.stream().mapToDouble(v -> v).max().orElse(1.0);
        double chartH = Math.max(10, c.height - 80);
        double baseY = c.y + c.height - 40;
        double startX = c.x + 40;

        out.append(String.format(Locale.ROOT, "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"#cbd5e1\" stroke-width=\"2\"/>\n", startX, baseY, c.x + c.width - 10, baseY));
        out.append(String.format(Locale.ROOT, "<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"#cbd5e1\" stroke-width=\"2\"/>\n", startX, c.y + 20, startX, baseY));

        for (int i = 0; i < values.size(); i++) {
            double h = (values.get(i) / max) * chartH;
            double x = startX + i * (c.barWidth + c.gap);
            double y = baseY - h;
            String color = c.palette != null && i < c.palette.size() ? c.palette.get(i) : "#6366f1";
            out.append(String.format(Locale.ROOT, "<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"%s\"/>\n", x, y, c.barWidth, Math.max(1, h), color));
        }
    }

    private void renderDonut(ChartElement c, StringBuilder out, List<Double> values) {
        double total = values.stream().mapToDouble(v -> v).sum();
        if (total <= 0) return;
        double cx = c.x + c.width / 2;
        double cy = c.y + c.height / 2;
        double outerR = Math.min(c.width, c.height) / 2 - 20;
        double innerR = outerR * c.donutInnerRatio;

        double angle = -Math.PI / 2;
        for (int i = 0; i < values.size(); i++) {
            double share = values.get(i) / total;
            double a0 = angle;
            double a1 = angle + share * Math.PI * 2;
            angle = a1;

            int steps = Math.max(4, (int) Math.round(share * 60));
            StringBuilder d = new StringBuilder();
            for (int s = 0; s <= steps; s++) {
                double t = a0 + (a1 - a0) * ((double) s / steps);
                double px = cx + Math.cos(t) * outerR;
                double py = cy + Math.sin(t) * outerR;
                d.append(String.format(Locale.ROOT, "%s %.2f %.2f ", s == 0 ? "M" : "L", px, py));
            }
            for (int s = steps; s >= 0; s--) {
                double t = a0 + (a1 - a0) * ((double) s / steps);
                double px = cx + Math.cos(t) * innerR;
                double py = cy + Math.sin(t) * innerR;
                d.append(String.format(Locale.ROOT, "L %.2f %.2f ", px, py));
            }
            d.append("Z");
            String color = c.palette != null && i < c.palette.size() ? c.palette.get(i) : "#6366f1";
            out.append(String.format("<path d=\"%s\" fill=\"%s\" stroke=\"#ffffff\" stroke-width=\"2\"/>\n", d, color));
        }
    }
}