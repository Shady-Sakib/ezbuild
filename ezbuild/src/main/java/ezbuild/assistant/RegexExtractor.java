package ezbuild.assistant;

import ezbuild.dto.BuildRequirements;
import ezbuild.dto.Intent;
import ezbuild.dto.UseCase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based extraction for when the LLM is off: budget, use case, brands, named parts, language (English,
 * Bangla, Banglish). Also fills gaps in the model's answer.
 */
@Component
public class RegexExtractor {

    private static final Pattern BUDGET = Pattern.compile(
            "(?:tk\\.?|bdt|৳)?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+(?:\\.\\d+)?)\\s*(k|K|hajar|hazar|thousand|lakh|lac|lakhs|হাজার|লাখ)?\\s*(?:tk|taka|takar|bdt|টাকা|৳)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PART = Pattern.compile(
            "(ryzen\\s*[3579]\\s*\\d{4}[a-z0-9]*|(?:core\\s*)?i[3579][\\s-]?\\d{4,5}[a-z]*|rtx\\s*\\d{4}(?:\\s*ti)?(?:\\s*super)?"
                    + "|gtx\\s*\\d{3,4}(?:\\s*ti)?|gt\\s*\\d{3,4}|rx\\s*\\d{4}(?:\\s*(?:xt|gre))?)",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> BRANDS = List.of("intel", "amd", "nvidia", "radeon", "asus", "msi", "gigabyte",
            "asrock", "corsair", "kingston", "g.skill", "teamgroup", "samsung", "wd", "crucial", "seagate", "deepcool",
            "cooler master", "nzxt", "lian li", "antec", "zotac", "sapphire", "powercolor", "pny", "adata", "lg",
            "viewsonic", "logitech", "redragon", "razer", "hyperx");

    public BuildRequirements extract(String message) {
        String text = toAsciiDigits(message == null ? "" : message);
        String lower = text.toLowerCase(Locale.ROOT);

        Long budget = budget(text);
        UseCase useCase = useCase(lower);
        List<String> brands = new ArrayList<>();
        for (String b : BRANDS) {
            if (Pattern.compile("\\b" + Pattern.quote(b) + "\\b").matcher(lower).find()) brands.add(b);
        }
        if (lower.matches(".*\\b(rtx|gtx|geforce)\\b.*") && !brands.contains("nvidia")) brands.add("nvidia");

        Set<String> parts = new LinkedHashSet<>();
        Matcher m = PART.matcher(lower);
        while (m.find()) parts.add(m.group(1).replaceAll("\\s+", " ").trim());

        List<String> must = new ArrayList<>();
        if (lower.contains("wifi") || lower.contains("wi-fi")) must.add("wifi");
        if (lower.matches(".*\\b(small|compact|mini|matx)\\b.*")) must.add("small");

        Integer storage = null;
        Matcher s = Pattern.compile("(\\d+)\\s*(tb|gb)\\s*(?:ssd|storage|nvme)", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (s.find()) storage = Integer.parseInt(s.group(1)) * ("tb".equalsIgnoreCase(s.group(2)) ? 1000 : 1);

        String language = language(message == null ? "" : message);
        Intent intent = intent(lower, budget, parts);
        String focus = focus(lower);
        boolean anyBrand = anyBrand(lower);
        if (intent == Intent.EXPLAIN && focus != null && !isComparison(lower)) intent = Intent.BUILD_FROM_BUDGET;
        boolean clarify = (intent == Intent.BUILD_FROM_BUDGET || intent == Intent.BUILD_AROUND_PARTS) && budget == null;
        String question = clarify ? "What is your budget in taka?" : null;
        return new BuildRequirements(intent, budget, useCase, brands, new ArrayList<>(parts), must, storage, null,
                language, clarify, question, focus, anyBrand);
    }

    /** "better/faster/stronger CPU", "more cores", "upgrade the processor" -> cpu; same idea for the GPU. */
    static String focus(String lower) {
        String better = "(better|faster|stronger|more powerful|best|good|valo|bhalo|upgrade(?:d)?|top|higher[- ]end|powerful)";
        if (lower.matches(".*\\b" + better + "\\s+(?:performing\\s+)?(cpu|processor|proc)\\b.*")
                || lower.matches(".*\\b(cpu|processor)\\s+(upgrade|first|priority|beshi|more)\\b.*")
                || lower.matches(".*\\bmore\\s+cores\\b.*")
                || lower.matches(".*\\b(prioriti[sz]e|focus on)\\s+(the\\s+)?(cpu|processor)\\b.*")) {
            return "cpu";
        }
        if (lower.matches(".*\\b" + better + "\\s+(?:performing\\s+)?(gpu|graphics(?: card)?|vga)\\b.*")
                || lower.matches(".*\\b(gpu|graphics card)\\s+(upgrade|first|priority|beshi|more)\\b.*")
                || lower.matches(".*\\b(prioriti[sz]e|focus on)\\s+(the\\s+)?(gpu|graphics)\\b.*")
                || lower.matches(".*\\bmore\\s+fps\\b.*")) {
            return "gpu";
        }
        return null;
    }

    /** "doesn't matter if it's intel or amd", "any brand", "either is fine". */
    static boolean anyBrand(String lower) {
        return lower.matches(".*\\b(doesn'?t|does not|dont|don'?t|no) matter\\b.*")
                || lower.matches(".*\\b(any|either|whichever) (brand|one|is fine|works)\\b.*")
                || lower.matches(".*\\bno (brand )?preference\\b.*")
                || lower.matches(".*\\b(intel or amd|amd or intel|nvidia or (amd|radeon)|(amd|radeon) or nvidia)\\b.*")
                || lower.matches(".*\\b(jekono|je kono|jei kono)\\b.*");
    }

    /** "7600 vs 12400F", "which is better", "faster than", "difference between". */
    static boolean isComparison(String lower) {
        return lower.matches(".*\\b(vs\\.?|versus|compare|comparison|difference|which (one )?is better)\\b.*")
                || lower.matches(".*\\b(better|faster|stronger|worse|slower) than\\b.*");
    }

    /** Follow-up that changes the last build: cheaper, instead, swap, change, more storage... */
    static boolean isRefinement(String lower) {
        return focus(lower) != null || anyBrand(lower)
                || lower.matches(".*\\b(cheaper|less expensive|lower budget|kom dam|kom dame|cheap|instead|swap|replace|change|"
                + "more storage|more ram|bigger ssd|white|rgb|wifi|smaller|same budget|this budget|ei budget)\\b.*")
                || lower.matches(".*\\b\\d+\\s*(tb|gb)\\s*(ssd|storage|nvme|ram)\\b.*");
    }

    static boolean wantsCheaper(String lower) {
        return lower.matches(".*\\b(cheaper|less expensive|lower budget|kom dam|kom dame|budget kom)\\b.*");
    }

    Long budget(String raw) {
        // strip model numbers (RTX 5060, i5-12400F) and specs (6000MHz, 1TB, 165Hz) so they aren't read as money
        String text = PART.matcher(raw).replaceAll(" ")
                .replaceAll("(?i)\\d+(?:\\.\\d+)?\\s*(mhz|gb|tb|hz|w|watt|p|mm|fps|rpm|inch|in)\\b", " ");
        Matcher m = BUDGET.matcher(text);
        Long best = null;
        while (m.find()) {
            String num = m.group(1).replace(",", "");
            String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
            double value;
            try {
                value = Double.parseDouble(num);
            } catch (NumberFormatException e) {
                continue;
            }
            if (unit.startsWith("k") || unit.startsWith("ha") || unit.equals("thousand") || unit.equals("হাজার")) value *= 1_000;
            else if (unit.startsWith("la") || unit.equals("লাখ")) value *= 100_000;
            else if (m.group(0).toLowerCase(Locale.ROOT).matches(".*(tk|taka|takar|bdt|৳|টাকা).*") || value >= 5_000) {
                // plain number with a currency word, or big enough to be a price
            } else {
                continue; // "16gb", "1080p", "ryzen 5"...
            }
            if (value >= 5_000 && value <= 5_000_000) {
                long v = Math.round(value);
                if (best == null || v > best) best = v;
            }
        }
        return best;
    }

    static UseCase useCase(String lower) {
        if (lower.matches(".*\\b(edit|editing|premiere|davinci|after effects|video)\\b.*")) return UseCase.VIDEO_EDITING;
        if (lower.matches(".*\\b(render|rendering|blender|3d|autocad|revit)\\b.*")) return UseCase.RENDER_3D;
        if (lower.matches(".*\\b(ai|ml|machine learning|deep learning|llm|stable diffusion|cuda)\\b.*")) return UseCase.AI_ML;
        if (lower.matches(".*\\b(stream|streaming|obs)\\b.*")) return UseCase.STREAMING;
        if (lower.matches(".*\\b(game|games|gaming|gamer|valorant|pubg|fortnite|gta|cs2|1080p|1440p|fps)\\b.*")) return UseCase.GAMING;
        if (lower.matches(".*\\b(office|study|school|browsing|excel|word|freelanc\\w*|programming|coding)\\b.*")) return UseCase.OFFICE;
        return UseCase.GENERAL;
    }

    static Intent intent(String lower, Long budget, Set<String> parts) {
        if (lower.matches(".*\\b(essay|poem|homework|assignment|story|weather|joke|translate|recipe)\\b.*")
                && !lower.matches(".*\\b(pc|build|computer|gpu|cpu)\\b.*")) {
            return Intent.OFF_TOPIC;
        }
        boolean buildWords = lower.matches(".*\\b(build|pc|computer|setup|rig|budget|lagbe|chai|banao|banaw|suggest)\\b.*")
                || lower.contains("পিসি") || lower.contains("কম্পিউটার");
        if (isComparison(lower) && budget == null) return Intent.EXPLAIN;
        if (!parts.isEmpty() && (budget != null || lower.matches(".*\\b(around|with|own|have|already|based)\\b.*"))) {
            return Intent.BUILD_AROUND_PARTS;
        }
        if (budget != null || buildWords) return Intent.BUILD_FROM_BUDGET;
        if (lower.matches(".*\\b(need|want|looking for|something for|recommend)\\b.*") && useCase(lower) != UseCase.GENERAL) {
            return Intent.BUILD_FROM_BUDGET;
        }
        if (!parts.isEmpty() || lower.matches(".*\\b(price|stock|available|cost|dam|koto)\\b.*")) return Intent.CATALOG_QUESTION;
        if (lower.matches(".*\\b(what is|explain|difference|vs|versus|compare|better|why|how)\\b.*")) return Intent.EXPLAIN;
        return Intent.SMALLTALK;
    }

    static String language(String original) {
        if (original.codePoints().anyMatch(c -> c >= 0x0980 && c <= 0x09FF)) return "bn";
        String lower = original.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(amar|ami|lagbe|chai|takar|hajar|hazar|kemon|dao|koro|kintu|valo|bhalo|kon|ki|ar)\\b.*")) {
            return "banglish";
        }
        return "en";
    }

    static String toAsciiDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch >= '০' && ch <= '৯') sb.append((char) ('0' + (ch - '০')));
            else sb.append(ch);
        }
        return sb.toString();
    }
}
