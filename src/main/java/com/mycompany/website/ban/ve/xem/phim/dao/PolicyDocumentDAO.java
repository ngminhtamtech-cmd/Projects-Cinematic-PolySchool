package com.mycompany.website.ban.ve.xem.phim.dao;

import com.mycompany.website.ban.ve.xem.phim.model.PolicyDocument;
import java.util.Optional;

public interface PolicyDocumentDAO {
    Optional<PolicyDocument> findPublished(String policyKey);
    Optional<PolicyDocument> findLatestDraft(String policyKey);
    PolicyDocument saveDraft(String policyKey, String title, String bodyText, int actorId);
    PolicyDocument publish(String policyKey, String title, String bodyText, int actorId);
}
