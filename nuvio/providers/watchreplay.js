/**
 * Nuvio Provider: WatchReplay (NFL & Sports Replays)
 * Direct Vidaraa JSON API + StreamWish extraction
 */

const BASE_URL = 'https://watchreplay.net';

function getCatalog(catalogId, page) {
    var p = page || 1;
    var url = BASE_URL + '/category/' + catalogId + '/' + (p > 1 ? 'page/' + p + '/' : '');

    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var items = [];
            var regex = /<article[^>]*class=["'][^"']*wr-card[^"']*["'][^>]*>[\s\S]*?<a[^>]*class=["']wr-card-cover["'][^>]*href=["']([^"']+)["'][^>]*aria-label=["']([^"']+)["'][\s\S]*?style=["'][^"']*url\(['"]?([^'")]+)['"]?\)/g;
            var m;
            while ((m = regex.exec(html)) !== null) {
                items.push({
                    id: m[1],
                    title: m[2].trim(),
                    poster: m[3],
                    type: 'series'
                });
            }
            return items;
        });
}

function search(query) {
    var url = BASE_URL + '/?s=' + encodeURIComponent(query.trim());
    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var items = [];
            var regex = /<article[^>]*class=["'][^"']*wr-card[^"']*["'][^>]*>[\s\S]*?<a[^>]*class=["']wr-card-cover["'][^>]*href=["']([^"']+)["'][^>]*aria-label=["']([^"']+)["']/g;
            var m;
            while ((m = regex.exec(html)) !== null) {
                items.push({
                    id: m[1],
                    title: m[2].trim(),
                    type: 'series'
                });
            }
            return items;
        });
}

function getDetails(url) {
    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var titleMatch = /<h1[^>]*>([\s\S]*?)<\/h1>/.exec(html);
            var title = titleMatch ? titleMatch[1].replace(/<[^>]+>/g, '').trim() : 'Sports Replay';

            var buttons = [];
            var bRegex = /href=["'](https?:\/\/(?:vidaraa\.cc|bysezoxexe\.com|streamwish\.to|playmate\.to|vixeo\.io)[^"']+)["']/g;
            var m;
            while ((m = bRegex.exec(html)) !== null) {
                buttons.push(m[1]);
            }

            return {
                id: url,
                title: title,
                type: 'series',
                episodes: [
                    {
                        id: JSON.stringify(buttons),
                        title: 'Full Game Replay',
                        episodeNumber: 1
                    }
                ]
            };
        });
}

function getStreams(epData) {
    var links = [];
    try { links = JSON.parse(epData); } catch(e) { links = [epData]; }

    var promises = links.map(function(link) {
        if (link.indexOf('vidaraa.cc') !== -1) {
            var m = /\/(?:e|watch)\/([a-zA-Z0-9]+)/.exec(link);
            if (!m) return Promise.resolve(null);
            var filecode = m[1];

            return fetch('https://vidaraa.cc/api/stream', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Referer': BASE_URL,
                    'Origin': 'https://vidaraa.cc',
                    'User-Agent': 'Mozilla/5.0'
                },
                body: JSON.stringify({ filecode: filecode, device: 'web', codecs: ['avc1'] })
            })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data && data.streaming_url) {
                    return {
                        name: 'Vidaraa 1080p HLS',
                        url: data.streaming_url,
                        quality: '1080p',
                        headers: { 'Referer': 'https://vidaraa.cc/' }
                    };
                }
                return null;
            })
            .catch(function() { return null; });
        }
        return Promise.resolve(null);
    });

    return Promise.all(promises).then(function(res) {
        return res.filter(function(x) { return x !== null; });
    });
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        getCatalog: getCatalog,
        search: search,
        getDetails: getDetails,
        getStreams: getStreams
    };
}
