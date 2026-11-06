package com.example.demo.crawler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * robots.txt의 해당 User-agent 그룹과 Allow/Disallow를 평가하는 작은 정책 파서입니다.
 * 별표 와일드카드, 끝 표시 $, 가장 긴 경로 우선, 같은 길이에서는 Allow 우선 규칙을 적용합니다.
 * robots.txt는 수집 경로 제어에 사용하며 콘텐츠 재사용 권한을 대신 판단하지는 않습니다.
 */
public final class RobotsPolicy {
    /** 인스턴스를 만들 필요가 없는 정적 규칙 평가 도구입니다. */
    private RobotsPolicy() { }

    /** 같은 User-agent 그룹의 지시들을 임시로 담는 내부 자료 구조입니다. */
    private static final class Group {
        final List<String> agents = new ArrayList<>();
        final List<Rule> rules = new ArrayList<>();
        boolean hasDirectives;
    }

    /** 경로 패턴과 허용 여부를 묶는 내부 값입니다. */
    private record Rule(String path, boolean allow) { }

    /** 텍스트 전체에서 적용할 그룹을 선택하고 요청 경로+쿼리에 가장 구체적으로 맞는 규칙을 평가합니다. */
    public static boolean isAllowed(String text, URI uri, String productToken) {
        List<Group> groups = new ArrayList<>();
        Group current = new Group();
        groups.add(current);
        for (String line : text.split("\\R")) {
            String cleaned = line.split("#", 2)[0].strip();
            int colon = cleaned.indexOf(':');
            if (colon < 0) continue;
            String key = cleaned.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = cleaned.substring(colon + 1).strip();
            if (key.equals("user-agent")) {
                if (current.hasDirectives) { current = new Group(); groups.add(current); }
                current.agents.add(value.toLowerCase(Locale.ROOT));
            } else if (!current.agents.isEmpty()) {
                current.hasDirectives = true;
                if ((key.equals("allow") || key.equals("disallow")) && !value.isEmpty())
                    current.rules.add(new Rule(value, key.equals("allow")));
            }
        }
        String token = productToken.toLowerCase(Locale.ROOT);
        int bestAgent = -1;
        for (Group group : groups) bestAgent = Math.max(bestAgent, specificity(group, token));
        String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        int bestRule = -1;
        boolean allowed = true;
        for (Group group : groups) {
            if (specificity(group, token) != bestAgent || bestAgent < 0) continue;
            for (Rule rule : group.rules) {
                String glob = rule.path();
                boolean end = glob.endsWith("$");
                if (end) glob = glob.substring(0, glob.length() - 1);
                String regex = "^" + String.join(".*", java.util.Arrays.stream(glob.split("\\*", -1)).map(Pattern::quote).toList()) + (end ? "$" : "");
                int length = glob.replace("*", "").length();
                if (Pattern.compile(regex).matcher(path).find() && (length > bestRule || (length == bestRule && rule.allow()))) {
                    bestRule = length;
                    allowed = rule.allow();
                }
            }
        }
        return allowed;
    }

    /** 제품 토큰에 맞는 가장 구체적인 User-agent 길이를 반환합니다. *는 공통 그룹으로 0입니다. */
    private static int specificity(Group group, String token) {
        return group.agents.stream().mapToInt(agent -> agent.equals("*") ? 0 : token.contains(agent) ? agent.length() : -1).max().orElse(-1);
    }
}
