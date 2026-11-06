package ru.ticket.recognition.shape;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.ticket.recognition.ImageEncoding;
import ru.ticket.recognition.RecognitionDraft.Item;

/**
 * Детерминированный контурный анализ замаскированного растра.
 *
 * <p>Для каждой связной компоненты:
 * <ul>
 *   <li>простая геометрия (circle/rect/triangle) → {@code ShapeElement};</li>
 *   <li>простой полигон (≤6 сторон) → {@code PathElement};</li>
 *   <li>сложная графика → {@code ImageElement} с точным кропом.</li>
 * </ul>
 * Решение «shape vs image» не выдумывает вектор там, где его нет.
 */
@Component
public class ContourShapeRecognizer implements ShapeRecognizer {

    private static final int MIN_AREA = 400;
    private static final int MAX_SHAPES = 64;
    private static final int COMPLEX_SIDES = 6;

    @Override
    public List<Item> recognize(BufferedImage image, boolean[] textMask) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[] ink = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (textMask[y * w + x]) continue;
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                ink[y * w + x] = r < 245 || g < 245 || b < 245;
            }
        }

        boolean[] visited = new boolean[w * h];
        List<Item> result = new ArrayList<>();

        for (int i = 0; i < ink.length && result.size() < MAX_SHAPES; i++) {
            if (!ink[i] || visited[i]) continue;
            List<int[]> component = floodFill(ink, visited, w, h, i);
            if (component.size() < MIN_AREA) continue;

            int minX = w, minY = h, maxX = 0, maxY = 0;
            for (int[] p : component) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            if (bw < 20 || bh < 20) continue;

            double fillRatio = (double) component.size() / (bw * bh);
            int approxSides = countDominantSides(component, minX, minY, bw, bh);

            // 1. Круг проверяем ПЕРВЫМ, так как его заполнение (π/4 ≈ 0.785)
            // больше порога прямоугольника (0.65). Если проверять прямоугольник
            // первым, круг будет ошибочно классифицирован как rect.
            if (approxSides <= 5 && Math.abs(fillRatio - Math.PI / 4) < 0.12) {
                result.add(new Item("shape", minX, minY, bw, bh,
                        Map.of("shape", "circle",
                                "fill", dominantColor(image, component),
                                "stroke", "transparent",
                                "strokeWidth", 0.0)));
            }
            // 2. Прямоугольник: почти полное заполнение bbox
            else if (approxSides <= 4 && fillRatio > 0.85) {
                result.add(new Item("shape", minX, minY, bw, bh,
                        Map.of("shape", "rect",
                                "fill", dominantColor(image, component),
                                "stroke", "transparent",
                                "strokeWidth", 0.0)));
            }
            // 3. Треугольник: 3 доминирующие стороны
            else if (approxSides == 3) {
                result.add(new Item("shape", minX, minY, bw, bh,
                        Map.of("shape", "triangle",
                                "fill", dominantColor(image, component),
                                "stroke", "transparent",
                                "strokeWidth", 0.0)));
            }
            // 4. Простой полигон
            else if (approxSides <= COMPLEX_SIDES) {
                result.add(new Item("path", minX, minY, bw, bh,
                        Map.of("d", polygonPath(minX, minY, bw, bh),
                                "fill", dominantColor(image, component),
                                "stroke", "transparent",
                                "strokeWidth", 0.0)));
            }
            // 5. Сложная графика: отдаем точный растровый кроп
            else {
                result.add(new Item("image", minX, minY, bw, bh,
                        Map.of("src", ImageEncoding.pngDataUrl(
                                image.getSubimage(minX, minY, bw, bh)))));
            }
        }
        return result;
    }

    private List<int[]> floodFill(boolean[] ink, boolean[] visited, int w, int h, int start) {
        List<int[]> component = new ArrayList<>();
        var stack = new ArrayDeque<Integer>();
        stack.push(start);
        visited[start] = true;
        while (!stack.isEmpty()) {
            int idx = stack.pop();
            int x = idx % w, y = idx / w;
            component.add(new int[]{x, y});
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                    int next = ny * w + nx;
                    if (ink[next] && !visited[next]) {
                        visited[next] = true;
                        stack.push(next);
                    }
                }
            }
        }
        return component;
    }

    private int countDominantSides(
            List<int[]> component, int x0, int y0, int bw, int bh) {

        int top = 0, right = 0, bottom = 0, left = 0;
        for (int[] p : component) {
            int relX = p[0] - x0, relY = p[1] - y0;
            if (relY < 2) top++;
            else if (relY > bh - 3) bottom++;
            else if (relX < 2) left++;
            else if (relX > bw - 3) right++;
        }
        int active = 0;
        for (int count : new int[]{top, right, bottom, left}) {
            if (count > 3) active++;
        }
        return active >= 4 ? 4 : active;
    }

    private String polygonPath(int x0, int y0, int bw, int bh) {
        int[][] anchors = {
                {x0, y0}, {x0 + bw / 2, y0}, {x0 + bw - 1, y0},
                {x0 + bw - 1, y0 + bh / 2}, {x0 + bw - 1, y0 + bh - 1},
                {x0 + bw / 2, y0 + bh - 1}, {x0, y0 + bh - 1}, {x0, y0 + bh / 2}
        };
        StringBuilder d = new StringBuilder("M ");
        for (int[] a : anchors) {
            d.append(a[0]).append(' ').append(a[1]).append(" L ");
        }
        d.setLength(d.length() - 2);
        d.append(" Z");
        return d.toString();
    }

    private String dominantColor(BufferedImage image, List<int[]> component) {
        var counts = new HashMap<Integer, Integer>();
        for (int[] p : component) {
            counts.merge(image.getRGB(p[0], p[1]) & 0xffffff, 1, Integer::sum);
        }
        int rgb = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0x000000);
        return String.format(Locale.ROOT, "#%06x", rgb);
    }
}