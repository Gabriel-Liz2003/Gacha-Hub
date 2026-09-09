package dev.gachahub.core;

import java.time.*;
import java.util.*;

/** Pure JVM production logic; all counts use integer arithmetic. */
public final class ResourceMath {
    private ResourceMath() {}
    public static long missing(long need, long owned) {
        if (need < 0 || owned < 0) throw new IllegalArgumentException("Negative materials");
        return Math.max(0L, need - owned);
    }
    public static Map<String,Long> sum(List<Map<String,Long>> steps) {
        Map<String,Long> out = new LinkedHashMap<>();
        for (Map<String,Long> step : steps) for (Map.Entry<String,Long> e : step.entrySet()) {
            if(e.getValue()<0) throw new IllegalArgumentException("Negative cost");
            out.merge(e.getKey(), e.getValue(), Math::addExact);
        }
        return out;
    }
    /** Equal weight per resource, so currency doesn't swallow boss progress. */
    public static double progress(Map<String,Long> need, Map<String,Long> allocated) {
        double total = 0; int entries = 0;
        for (Map.Entry<String,Long> e : need.entrySet()) {
            if(e.getValue()<0) throw new IllegalArgumentException("Negative cost");
            if (e.getValue() == 0) continue;
            long held = allocated.getOrDefault(e.getKey(),0L);
            if(held<0) throw new IllegalArgumentException("Negative inventory");
            total += Math.min(1.0,(double)held/e.getValue()); entries++;
        }
        return entries == 0 ? 1.0 : total/entries;
    }
    /** Allocate shared inventory once, in caller's stable priority order. */
    public static List<Map<String,Long>> allocate(List<Map<String,Long>> projects, Map<String,Long> inventory) {
        Map<String,Long> remaining = new HashMap<>(inventory);
        if(remaining.values().stream().anyMatch(n->n<0)) throw new IllegalArgumentException("Negative inventory");
        List<Map<String,Long>> result = new ArrayList<>();
        for(Map<String,Long> p:projects) {
            Map<String,Long> a = new LinkedHashMap<>();
            for(Map.Entry<String,Long> e:p.entrySet()) {
                if(e.getValue()<0) throw new IllegalArgumentException("Negative cost");
                long n = Math.min(e.getValue(), remaining.getOrDefault(e.getKey(),0L));
                a.put(e.getKey(),n); remaining.put(e.getKey(),remaining.getOrDefault(e.getKey(),0L)-n);
            }
            result.add(a);
        }
        return result;
    }
    public static String compare(double actual, double low, double adequate, double excellent) {
        if (!Double.isFinite(actual) || actual < 0 || !Double.isFinite(low) || !Double.isFinite(adequate)
            || !Double.isFinite(excellent) || !(0 < low && low <= adequate && adequate <= excellent))
            throw new IllegalArgumentException("Invalid benchmark");
        return actual >= excellent ? "Excelente" : actual >= adequate ? "Adequado" : actual >= low ? "Precisa melhorar" : "Muito abaixo";
    }
    public static long estimatedEnergy(long missing, double yield, int cost) {
        if(missing<0 || !Double.isFinite(yield) || yield<=0 || cost<=0) throw new IllegalArgumentException("Invalid yield");
        double runs = Math.ceil(missing/yield);
        if(runs>Long.MAX_VALUE/cost) throw new ArithmeticException("Overflow");
        return Math.multiplyExact((long)runs,cost);
    }
    public static int serverDay(long epochSeconds, int offsetHours, int resetHour) {
        if(offsetHours < -12 || offsetHours > 14 || resetHour<0 || resetHour>23) throw new IllegalArgumentException("Invalid reset");
        return Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.ofHours(offsetHours)).minusHours(resetHour).getDayOfWeek().getValue();
    }
    public static boolean cacheFresh(long now, long fetched, long expires) {
        return now >= fetched && now < expires;
    }
    public static boolean matches(String name, String element, String role, String specialty, String query) {
        String hay = java.text.Normalizer.normalize(name+" "+element+" "+role+" "+specialty,java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        String q = java.text.Normalizer.normalize(query,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();
        return Arrays.stream(q.split("\\s+")).allMatch(hay::contains);
    }
}
