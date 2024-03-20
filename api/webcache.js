// api/webcache.js

export default async function handler(req, res) {
    const { url } = req.query;
    if (!url) {
        return res.status(400).json({ error: "URL is required" });
    }

    const googleCacheUrl = `http://webcache.googleusercontent.com/search?q=cache:${url}`;
    try {
        const response = await fetch(googleCacheUrl);
        let html = await response.text();

        // Strip out the header HTML using regular expressions
        html = html.replace(/<div id="bN015htcoyT__google-cache-hdr">[\s\S]*?<\/div>/, '');

        res.status(200).send(html);
    } catch (error) {
        console.error("Error fetching data:", error);
        res.status(500).json({ error: "Failed to fetch content from Google Web Cache" });
    }
}
