# ninefold

<img width="2195" height="1285" alt="image" src="https://github.com/user-attachments/assets/f5b9be57-3e92-40ee-881a-f46293d212e6" />
---
<img width="1440" height="1980" alt="ninefold-top9" src="https://github.com/user-attachments/assets/ebbc0e2f-3aa3-4088-9bec-06dce4c148cb" />


Pick your top 9 albums, EPs and singles. Make a board for your lore or current era, then share a link or PNG.

[Live Action](https://ninefold.duckdns.org/) · [Frontend repo](https://github.com/yofujitsu/lore-fm-frontend)

## Quick start

Requires Java 21, Maven 3.9+ and Node.js 22.12+. Set `JAVA_HOME` to your JDK.

```sh
git clone https://github.com/yofujitsu/lore-fm.git
cd lore-fm
cp .env.example .env
```

Edit `.env` with your catalog settings:

- **Spotify:** create an app in the [Developer Dashboard](https://developer.spotify.com/dashboard), select Web API, then copy Client ID and Client Secret from Settings. [Spotify requires Premium for Web API access](https://developer.spotify.com/documentation/web-api/tutorials/getting-started).
- **MusicBrainz:** [no API key needed](https://musicbrainz.org/doc/MusicBrainz_API). Enable it and use your own email in the User-Agent below.

```dotenv
SPOTIFY_CLIENT_ID=your-client-id
SPOTIFY_CLIENT_SECRET=your-client-secret
MUSICBRAINZ_ENABLED=true
MUSICBRAINZ_USER_AGENT=Ninefold/1.0.0 (mailto:you@example.com)
```

Keep credentials in the backend `.env` and restart the backend after changing them.

Start the backend from `lore-fm`:

```sh
mvn spring-boot:run
```

Open another terminal in `lore-fm` and start the frontend:

```sh
cd ../lore-fm-frontend
npm ci
npm run dev
```

Open [127.0.0.1:5173](http://127.0.0.1:5173/). The backend runs on port 8080.

To build locally, run `mvn verify` in `lore-fm` and `npm run build` in `lore-fm-frontend`.

## Contributing

Open an issue or send a focused pull request. Run `mvn verify` before submitting backend changes. Follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

[MIT](LICENSE). Music artwork and provider assets keep their own terms; see [Third-party notices](THIRD_PARTY_NOTICES.md).
