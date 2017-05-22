package org.mozilla.focus.web;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.customtabs.CustomTabsIntent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.focus.utils.SafeIntent;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class CustomTabConfigTest {

    /**
     * This class can't be unparceled, and can therefore be used to test that SafeIntent and SafeBundle
     * work as expected.
     */
    private static class UnparcelableParcel implements Parcelable {
        // Called when constructing for test purposes
        UnparcelableParcel() {
        }

        // Used only when unparceling:
        protected UnparcelableParcel(Parcel in) {
            throw new RuntimeException("Haha");
        }

        public static final Creator<UnparcelableParcel> CREATOR = new Creator<UnparcelableParcel>() {
            @Override
            public UnparcelableParcel createFromParcel(Parcel in) {
                return new UnparcelableParcel(in);
            }

            @Override
            public UnparcelableParcel[] newArray(int size) {
                return new UnparcelableParcel[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }

    @Test
    public void isCustomTabIntent() throws Exception {
        final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        final CustomTabsIntent customTabsIntent = builder.build();

        assertTrue(CustomTabConfig.isCustomTabIntent(new SafeIntent(customTabsIntent.intent)));
    }

    @Test
    public void menuTest() throws Exception {
        final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        final PendingIntent pendingIntent = PendingIntent.getActivity(null, 0, null, 0);

        builder.addMenuItem("menuitem1", pendingIntent);
        builder.addMenuItem("menuitem2", pendingIntent);
        // We can only handle menu items with an actual PendingIntent, other ones should be ignored:
        builder.addMenuItem("menuitemIGNORED", null);

        final CustomTabsIntent customTabsIntent = builder.build();

        final CustomTabConfig config = CustomTabConfig.parseCustomTabIntent(RuntimeEnvironment.application, new SafeIntent(customTabsIntent.intent));

        assertEquals("Menu should contain 2 items", 2, config.menuItems.size());
        final String s = config.menuItems.get(0).name;
        assertEquals("Unexpected menu item",
                "menuitem1", config.menuItems.get(0).name);
        assertEquals("Unexpected menu item",
                "menuitem2", config.menuItems.get(1).name);
    }

    @Test
    public void malformedExtras() throws Exception {
        final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        final CustomTabsIntent customTabsIntent = builder.build();

        customTabsIntent.intent.putExtra("garbage", new UnparcelableParcel());

        // We write the extras into a parcel so that we can check what happens when unparcelling fails
        final Parcel parcel = Parcel.obtain();
        final Bundle extras = customTabsIntent.intent.getExtras();
        extras.writeToParcel(parcel, 0);
        extras.clear();

        // Bundle is lazy and doesn't unparcel when calling readBundle()
        parcel.setDataPosition(0);
        final Bundle injectedBundle = parcel.readBundle();
        parcel.recycle();

        // We aren't usually allowed to overwrite an intent's extras:
        final Field extrasField = Intent.class.getDeclaredField("mExtras");
        extrasField.setAccessible(true);
        extrasField.set(customTabsIntent.intent, injectedBundle);
        extrasField.setAccessible(false);

        // And we can't access any extras now because unparcelling fails:
        assertFalse(CustomTabConfig.isCustomTabIntent(new SafeIntent(customTabsIntent.intent)));

        // Ensure we don't crash regardless
        final CustomTabConfig c = CustomTabConfig.parseCustomTabIntent(RuntimeEnvironment.application, new SafeIntent(customTabsIntent.intent));

        // And we don't have any data:
        assertNull(c.actionButtonConfig);
    }

    @Test
    public void malformedActionButtonConfig() throws Exception {
        final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        final CustomTabsIntent customTabsIntent = builder.build();

        final Bundle garbage = new Bundle();
        garbage.putParcelable("foobar", new UnparcelableParcel());
        customTabsIntent.intent.putExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE, garbage);

        // We should still detect that this is a custom tab
        assertTrue(CustomTabConfig.isCustomTabIntent(new SafeIntent(customTabsIntent.intent)));

        // And we still don't crash
        final CustomTabConfig.ActionButtonConfig actionButtonConfig = CustomTabConfig.getActionButtonConfig(RuntimeEnvironment.application, new SafeIntent(customTabsIntent.intent));

        // But we weren't able to read the action button data because of the unparcelable data
        assertNull(actionButtonConfig);
    }

    @Test
    public void actionButtonConfig() throws Exception {
        final CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        final String description = "description";
        final String intentAction = "ACTION";
        {
            final Bitmap bitmap = Bitmap.createBitmap(new int[]{Color.RED}, 1, 1, Bitmap.Config.ARGB_8888);
            final PendingIntent intent = PendingIntent.getActivity(RuntimeEnvironment.application, 0, new Intent(intentAction), 0);

            builder.setActionButton(bitmap, description, intent);
        }

        final CustomTabsIntent customTabsIntent = builder.build();
        final CustomTabConfig.ActionButtonConfig actionButtonConfig = CustomTabConfig.getActionButtonConfig(RuntimeEnvironment.application, new SafeIntent(customTabsIntent.intent));

        assertEquals(description, actionButtonConfig.description);
        assertNotNull(actionButtonConfig.pendingIntent);

        final Bitmap bitmap = actionButtonConfig.icon;
        assertEquals(1, bitmap.getWidth());
        assertEquals(1, bitmap.getHeight());
        assertEquals(Color.RED, bitmap.getPixel(0, 0));
    }
}