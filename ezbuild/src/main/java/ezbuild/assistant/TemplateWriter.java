package ezbuild.assistant;

import ezbuild.build.BuildPlanner;
import ezbuild.dto.BuildPlan;
import ezbuild.dto.BuildRequirements;
import ezbuild.dto.PlanPart;
import ezbuild.model.Product;
import ezbuild.model.ProductCategory;
import ezbuild.util.Fmt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Offline-mode reply text, plus the fact sheets given to the model. Everything here comes from catalogue
 * data.
 */
@Component
public class TemplateWriter {

    // ---- fact sheets for the LLM

    /** Plans with every part's specs, power headroom and CPU/GPU balance, so the model can explain why. */
    public String planSummary(List<BuildPlan> plans, Map<String, Product> byId, BuildRequirements req) {
        StringBuilder sb = new StringBuilder();
        for (BuildPlan plan : plans) {
            sb.append("Tier ").append(plan.tier()).append(": total ").append(Fmt.taka(plan.total()));
            if (plan.budget() > 0) sb.append(" of budget ").append(Fmt.taka(plan.budget()))
                    .append(" (").append(Fmt.taka(plan.leftover())).append(" left)");
            sb.append("\n");
            for (PlanPart p : plan.parts()) {
                Product full = byId.get(p.productId());
                sb.append("  - ").append(p.category().getLabel()).append(": ").append(p.name())
                        .append(" (").append(Fmt.taka(p.price()));
                if (p.listPrice() > p.price()) sb.append(", discounted from ").append(Fmt.taka(p.listPrice()));
                sb.append(")");
                if (full != null) sb.append(" | ").append(specLine(full));
                sb.append("\n");
            }
            Product cpu = part(plan, ProductCategory.CPU, byId);
            Product gpu = part(plan, ProductCategory.GPU, byId);
            if (cpu != null) {
                sb.append("  Scores: CPU ").append(Math.round(BuildPlanner.cpuScore(cpu, req.useCase())))
                        .append("/100 for ").append(req.useCase().getLabel().toLowerCase());
                if (gpu != null) sb.append(", GPU ").append(Math.round(BuildPlanner.gpuScore(gpu))).append("/100 gaming index");
                sb.append("\n");
            }
            Product psu = part(plan, ProductCategory.PSU, byId);
            if (psu != null) {
                int peak = plan.compat().estimatedWattage();
                sb.append("  Power: estimated peak draw ").append(peak).append(" W (CPU turbo ")
                        .append(ezbuild.service.CompatibilityService.cpuPeak(cpu)).append(" W")
                        .append(gpu == null ? "" : ", GPU " + gpu.specInt("tdp") + " W")
                        .append("), PSU ").append(psu.specInt("watt")).append(" W ")
                        .append(Objects.toString(psu.spec("eff"), "")).append(" = ")
                        .append(Math.round((psu.specInt("watt") - peak) * 100.0 / Math.max(1, peak))).append("% headroom\n");
            }
            double ratio = ezbuild.service.CompatibilityService.bottleneckRatio(cpu, gpu);
            if (!Double.isNaN(ratio)) {
                sb.append("  Balance: CPU/GPU gaming ratio ").append(String.format(java.util.Locale.ROOT, "%.2f", ratio))
                        .append(ratio < 0.85 ? " (CPU-limited)" : ratio > 2.2 ? " (GPU-limited)" : " (well balanced)").append("\n");
            }
            if (!plan.compat().warnings().isEmpty()) {
                sb.append("  Notes: ").append(String.join(" ", plan.compat().warnings())).append("\n");
            }
        }
        return sb.toString();
    }

    /** One product as a fact line: price, stock, every spec with a label. */
    public String productFacts(Product p) {
        return p.getName() + " — " + Fmt.taka(p.effectivePrice())
                + (p.onSale() ? " (" + p.activeDiscount() + "% off " + Fmt.taka(p.getPrice()) + ")" : "")
                + (p.inStock() ? ", in stock" : ", OUT OF STOCK") + " | " + specLine(p)
                + (p.getSummary() == null ? "" : " | " + p.getSummary());
    }

    public String specLine(Product p) {
        if (p.getSpecs() == null || p.getSpecs().isEmpty()) return "";
        return p.getSpecs().entrySet().stream()
                .filter(e -> !"none".equals(String.valueOf(e.getValue())))
                .map(e -> Fmt.label(e.getKey()) + ": " + Fmt.specValue(e.getValue()))
                .collect(Collectors.joining("; "));
    }

    /** Previous suggestion as "tier: CPU (score) + GPU (score)" so the model can say what changed. */
    public String previousSummary(List<BuildPlan> previous, Map<String, Product> byId, BuildRequirements req) {
        if (previous == null || previous.isEmpty()) return "";
        return previous.stream().map(plan -> {
            Product cpu = part(plan, ProductCategory.CPU, byId);
            Product gpu = part(plan, ProductCategory.GPU, byId);
            return plan.tier() + " " + Fmt.taka(plan.total()) + ": "
                    + (cpu == null ? "?" : cpu.getName() + " (CPU score " + Math.round(BuildPlanner.cpuScore(cpu, req.useCase())) + ")")
                    + (gpu == null ? "" : " + " + gpu.getName() + " (GPU score " + Math.round(BuildPlanner.gpuScore(gpu)) + ")");
        }).collect(Collectors.joining("\n"));
    }

    // ---- offline-mode replies

    public String builds(BuildRequirements req, List<BuildPlan> plans, Map<String, Product> byId) {
        boolean bangla = !"en".equals(req.replyLanguage());
        String budget = req.budgetBdt() == null ? "" : Fmt.taka(req.budgetBdt());
        String use = req.useCase().getLabel().toLowerCase();
        StringBuilder sb = new StringBuilder();
        if (bangla) {
            sb.append("Apnar ").append(budget).append(" budget-e ").append(use).append(" er jonno ")
                    .append(plans.size()).append(" ta compatible build dilam. ");
            if ("cpu".equals(req.focus())) sb.append("Apni shokti-shali CPU cheyechen, tai CPU-ke priority diyechi. ");
            sb.append("Protiti CPU + GPU combination test kore balanced build ta beche niyechi:\n\n");
        } else {
            sb.append("Here ").append(plans.size() == 1 ? "is a build" : "are " + plans.size() + " builds")
                    .append(" for ").append(use).append(budget.isEmpty() ? "" : " within " + budget).append(". ");
            if ("cpu".equals(req.focus())) sb.append("You asked for a stronger processor, so the CPU got priority this time. ");
            if ("gpu".equals(req.focus())) sb.append("You asked for more graphics power, so the GPU got priority this time. ");
            sb.append("I tested every CPU and graphics card combination that fits and kept the strongest balanced one at each price point:\n\n");
        }
        for (BuildPlan p : plans) {
            Product cpu = part(p, ProductCategory.CPU, byId);
            Product gpu = part(p, ProductCategory.GPU, byId);
            sb.append("**").append(p.tier()).append(" – ").append(Fmt.taka(p.total())).append("**\n");
            if (cpu != null) sb.append("- CPU: ").append(cpu.getName()).append(" (").append(cpuBrief(cpu)).append(")\n");
            if (gpu != null) sb.append("- GPU: ").append(gpu.getName()).append(" (").append(gpuBrief(gpu)).append(")\n");
            Product ram = part(p, ProductCategory.RAM, byId);
            Product ssd = part(p, ProductCategory.STORAGE, byId);
            if (ram != null || ssd != null) {
                sb.append("- ").append(ram == null ? "" : ram.getSpecs().getOrDefault("gb", "?") + " GB " + Objects.toString(ram.spec("type"), ""))
                        .append(ram != null && ssd != null ? " + " : "")
                        .append(ssd == null ? "" : ssd.getName()).append("\n");
            }
            if (p.leftover() > 0) sb.append("- ").append(Fmt.taka(p.leftover())).append(bangla ? " budget theke baki thake" : " left from your budget").append("\n");
            sb.append("\n");
        }
        sb.append(bangla
                ? "Shob part stock-e ache ar compatibility check kora. Kon ta pochondo? Ami brand change, storage barano ba aro kom dame korte pari."
                : "Every part is in stock and compatibility-checked. Which one feels right? I can switch brands, add storage, or bring the price down.");
        return sb.toString();
    }

    public String tooLow(BuildRequirements req, long minimum) {
        if (!"en".equals(req.replyLanguage())) {
            return "Ei budget-e compatible build kora jacche na. Ei requirement-er jonno shob theke kom dami build prai "
                    + Fmt.taka(minimum) + ". Budget ektu barale ami build dite parbo.";
        }
        return "That budget is a bit too tight for a complete, compatible build with these requirements: the cheapest valid "
                + "build right now comes to about " + Fmt.taka(minimum) + ". If you can stretch to that, or drop a requirement, "
                + "I'll plan it straight away.";
    }

    public String askBudget(BuildRequirements req) {
        if (!"en".equals(req.replyLanguage())) return "Shundor! Apnar budget koto taka? (jemon: 80 hajar ba 1.2 lakh) Ar PC ta ki kaje lagbe – gaming, office na editing?";
        return "Happy to help! What's your budget in taka (for example 80k or 1.2 lakh), and what will you mostly use the PC for – gaming, office work, editing?";
    }

    public String offTopic(BuildRequirements req) {
        if (!"en".equals(req.replyLanguage())) return "Ami shudhu PC build ar EZBuild er product niye help korte pari. Apnar PC er budget ki?";
        return "I can only help with PC building and the EZBuild catalogue. Tell me your budget and what you'll use the PC for, and I'll plan a build.";
    }

    public String greeting(BuildRequirements req) {
        if (!"en".equals(req.replyLanguage())) return "Hi! Ami EZBuild assistant. Budget ar kaj (gaming, office, editing) bolen, ami build sajiye dibo.";
        return "Hi! I'm the EZBuild assistant. Tell me your budget and what the PC is for (gaming, office, editing...) "
                + "or name parts you want, and I'll put together compatible builds from our stock and explain the choices.";
    }

    public String explainFallback() {
        return "Good question! The AI part of the assistant is offline right now, so I can't give a full answer, but I can still "
                + "plan builds: tell me your budget and use, and every card shows the parts with their specs.";
    }

    // ---- helpers

    private static Product part(BuildPlan plan, ProductCategory c, Map<String, Product> byId) {
        return plan.parts().stream().filter(p -> p.category() == c).findFirst()
                .map(p -> byId.get(p.productId())).orElse(null);
    }

    static String cpuBrief(Product cpu) {
        if (cpu.specInt("cores") == 0) return cpu.getSummary();
        return cpu.specInt("cores") + " cores / " + cpu.specInt("threads") + " threads, up to " + cpu.spec("boost") + " GHz"
                + (cpu.spec("arch") == null ? "" : ", " + cpu.spec("arch"));
    }

    static String gpuBrief(Product gpu) {
        if (gpu.specInt("vram") == 0) return gpu.getSummary();
        return gpu.specInt("vram") + " GB " + Objects.toString(gpu.spec("vramType"), "")
                + (gpu.spec("upscaler") == null || "none".equals(gpu.spec("upscaler")) ? "" : ", " + gpu.spec("upscaler"));
    }
}
