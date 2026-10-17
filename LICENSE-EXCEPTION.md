# Additional permission under GNU GPL version 3 section 7

Orbit for Android is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).

The `play` build adds Google Cast, which uses these Google Play services libraries, covered by the Android Software Development Kit License rather than a free software license:

- `com.google.android.gms:play-services-cast-framework`
- `com.google.android.gms:play-services-cast`
- `com.google.android.gms:play-services-base`
- `com.google.android.gms:play-services-basement`
- `com.google.android.gms:play-services-tasks`
- `com.google.android.gms:play-services-flags`

The other libraries that come with them are free software (Apache License 2.0). The `libre` build includes none of these. For the `play` build:

If you modify this Program, or any covered work, by linking or combining it with the Google Play services libraries (or a modified version of those libraries), containing parts covered by the terms of the Android Software Development Kit License, the licensors of this Program grant you additional permission to convey the resulting work.
