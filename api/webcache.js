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
        

        // Remove the "Cached" header
        html = html.replace(/<div id="bN015htcoyT__google-cache-hdr">[\s\S]*?<\/div>/, '');

        // Remove the "View source" link
        html = html.replace(/<span>\s*View source\s*<\/span>/gi, '');
        html = html.replace(/<span>\s*Text-only version\s*<\/span>/gi, '');

        // Align remaining content to the center
        html = html.replace(/<div><span style="display:inline-block;margin-top:8px;margin-right:104px;white-space:nowrap">/, '<div style="text-align: center;">');

        res.status(200).send(html);
    } catch (error) {
        console.error("Error fetching data:", error);
        res.status(500).json({ error: "Failed to fetch content from Google Web Cache" });
    }
}
