/**
 * Nuvio Provider: MotoMundo (MotoGP & WorldSBK)
 * Unpacks motomundo.top and luluvido.com embedded streams
 */

const BASE_URL = 'https://motomundo.net';

function getCatalog(catalogId, page) {
    var p = page || 1;
    var url = BASE_URL + '/category/' + catalogId + '/' + (p > 1 ? 'page/' + p + '/' : '');

    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var items = [];
            var regex = /<article[^>]*>[\s\S]*?<a[^>]*href=["']([^"']+)["'][^>]*rel=["']bookmark["'][^>]*>([\s\S]*?)<\/a>[\s\S]*?<img[^>]*src=["']([^"']+)["']/g;
            var m;
            while ((m = regex.exec(html)) !== null) {
                items.push({
                    id: m[1],
                    title: m[2].replace(/<[^>]+>/g, '').trim(),
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
            var regex = /<article[^>]*>[\s\S]*?<a[^>]*href=["']([^"']+)["'][^>]*rel=["']bookmark["'][^>]*>([\s\S]*?)<\/a>[\s\S]*?<img[^>]*src=["']([^"']+)["']/g;
            var m;
            while ((m = regex.exec(html)) !== null) {
                items.push({
                    id: m[1],
                    title: m[2].replace(/<[^>]+>/g, '').trim(),
                    poster: m[3],
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
            var title = titleMatch ? titleMatch[1].replace(/<[^>]+>/g, '').trim() : 'MotoGP Round';

            var iframes = [];
            var ifRegex = /<iframe[^>]*(?:src|data-src)=["']([^"']+)["']/g;
            var m;
            while ((m = ifRegex.exec(html)) !== null) {
                var src = m[1];
                if (src.indexOf('timing.motomundo') === -1 && (src.indexOf('motomundo.top') !== -1 || src.indexOf('luluvido.com') !== -1)) {
                    iframes.push(src);
                }
            }

            var episodes = [];
            if (iframes.length > 0) {
                episodes.push({
                    id: JSON.stringify(iframes),
                    title: 'Full Event Sessions',
                    episodeNumber: 1
                });
            }

            return {
                id: url,
                title: title,
                type: 'series',
                episodes: episodes
            };
        });
}

function getStreams(epData) {
    var urls = [];
    try {
        urls = JSON.parse(epData);
    } catch(e) {
        urls = [epData];
    }

    var promises = urls.slice(0, 4).map(function(link) {
        return fetch(link, {
            headers: {
                'User-Agent': 'Mozilla/5.0',
                'Referer': BASE_URL
            }
        })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            // Find m3u8
            var m = /["'](https?:\/\/[^"']+\.m3u8[^"']*)["']/.exec(html);
            if (m) {
                return {
                    name: 'MotoMundo Stream (' + (link.indexOf('motomundo') !== -1 ? 'Fast' : 'Mirror') + ')',
                    url: m[1],
                    quality: '1080p',
                    headers: { 'Referer': link }
                };
            }
            return null;
        })
        .catch(function() { return null; });
    });

    return Promise.all(promises).then(function(results) {
        return results.filter(function(r) { return r !== null; });
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
