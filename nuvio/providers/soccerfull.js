/**
 * Nuvio Provider: SoccerFull (Football Replays)
 * Direct 1080p HLS playback via https://soccerfull.net/hls/{sid}.m3u8
 */

const BASE_URL = 'https://soccerfull.net';

function getCatalog(catalogId, page) {
    var p = page || 1;
    var url = BASE_URL + '/cate/' + catalogId + (p > 1 ? '/' + p : '');
    
    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var items = [];
            var regex = /<li[^>]*class=["']item-movie["'][^>]*>[\s\S]*?<a[^>]*href=["']([^"']+)["'][^>]*title=["']([^"']+)["'][\s\S]*?<img[^>]*src=["']([^"']+)["']/g;
            var match;
            while ((match = regex.exec(html)) !== null) {
                var link = match[1];
                var title = match[2];
                var poster = match[3];
                if (link.indexOf('http') !== 0) link = BASE_URL + link;
                if (poster.indexOf('http') !== 0) poster = BASE_URL + poster;
                
                items.push({
                    id: link,
                    title: title,
                    poster: poster,
                    type: 'series'
                });
            }
            return items;
        });
}

function search(query) {
    var q = query.trim().replace(/\s+/g, '-').toLowerCase();
    var url = BASE_URL + '/search/' + q;
    
    return fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0' } })
        .then(function(res) { return res.text(); })
        .then(function(html) {
            var items = [];
            var regex = /<li[^>]*class=["']item-movie["'][^>]*>[\s\S]*?<a[^>]*href=["']([^"']+)["'][^>]*title=["']([^"']+)["'][\s\S]*?<img[^>]*src=["']([^"']+)["']/g;
            var match;
            while ((match = regex.exec(html)) !== null) {
                var link = match[1];
                var title = match[2];
                var poster = match[3];
                if (link.indexOf('http') !== 0) link = BASE_URL + link;
                if (poster.indexOf('http') !== 0) poster = BASE_URL + poster;
                
                items.push({
                    id: link,
                    title: title,
                    poster: poster,
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
            var title = titleMatch ? titleMatch[1].trim() : 'Football Match';
            
            var episodes = [];
            var sRegex = /<a[^>]*class=["'][^"']*video-server[^"']*["'][^>]*href=["'][^"']*sid=(\d+)["'][^>]*>([\s\S]*?)<\/a>/g;
            var m;
            var idx = 1;
            while ((m = sRegex.exec(html)) !== null) {
                episodes.push({
                    id: m[1],
                    title: m[2].replace(/<[^>]+>/g, '').trim(),
                    episodeNumber: idx++
                });
            }
            
            if (episodes.length === 0) {
                var defSid = /sid=(\d+)/.exec(html);
                episodes.push({
                    id: defSid ? defSid[1] : '1',
                    title: 'Full Match',
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

function getStreams(episodeId) {
    // episodeId is the sid
    var streamUrl = BASE_URL + '/hls/' + episodeId + '.m3u8';
    return Promise.resolve([
        {
            name: 'SoccerFull 1080p HLS',
            url: streamUrl,
            quality: '1080p',
            headers: {
                'Referer': BASE_URL + '/play/' + episodeId,
                'User-Agent': 'Mozilla/5.0'
            }
        }
    ]);
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        getCatalog: getCatalog,
        search: search,
        getDetails: getDetails,
        getStreams: getStreams
    };
}
