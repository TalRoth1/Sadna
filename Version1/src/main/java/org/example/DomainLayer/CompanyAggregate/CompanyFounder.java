package org.example.DomainLayer.CompanyAggregate;

public class CompanyFounder extends CompanyOwner {
    public CompanyFounder(String username) {
        super(username, null);
    }

    @Override
    public void buildMermaid(StringBuilder sb) {
        sb.append(mermaidId()).append("[")
                .append('"').append(getUsername()).append(" (Founder)")
                .append('"').append("]\n");
        for (ICompanyMember sub : getSubordinates()) {
            sub.buildMermaid(sb);
        }
    }
}
