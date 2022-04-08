package space.cherryband.ari;

/**
 * Created by itkach on 9/24/14.
 */
public interface LookupListener {
    void onLookupStarted(String query);

    void onLookupFinished(String query);

    void onLookupCanceled(String query);
}
