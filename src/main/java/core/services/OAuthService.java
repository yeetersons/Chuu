package core.services;

import core.apis.last.ConcurrentLastFM;
import core.exceptions.LastFmException;
import core.exceptions.UnknownLastFmException;
import dao.ChuuService;
import dao.entities.LastFMData;

import javax.annotation.Nullable;

public class OAuthService {
    private final ChuuService db;
    private final ConcurrentLastFM lastFM;

    public OAuthService(ChuuService db, ConcurrentLastFM lastFM) {
        this.db = db;
        this.lastFM = lastFM;
    }

    public String generateURL(String url, @Nullable LastFMData lastFMData) throws LastFmException {
        if (lastFMData == null) {
            return url;
        }
        try {
            String session;
            if (lastFMData.getToken() != null) {
                String authSession = lastFM.getAuthSession(lastFMData);
                db.storeSess(authSession, lastFMData.getName());
                lastFMData.setSession(authSession);
            }
            if (lastFMData.getSession() != null) {
                return lastFM.generateAuthoredCall(url, lastFMData.getSession());
            }
            return url;
        } catch (UnknownLastFmException exception) {
            if (exception.getCode() == 9) {
                db.clearSess(lastFMData.getSession(), lastFMData.getName());
            }
            throw exception;
        }
    }
}
