let currentGameData = null;
let currentRating = 0;
let allMyGames = [];
let sortableInstance = null;

document.addEventListener('DOMContentLoaded', () => {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') searchWeb();
        });
    }

    const modal = document.getElementById('gameModal');
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }
});

function switchTab(tab) {
    document.querySelectorAll('nav button').forEach(b => b.classList.remove('active'));
    document.getElementById(`tab-${tab}`).classList.add('active');
    document.querySelectorAll('.view-section').forEach(v => v.classList.remove('active'));
    document.getElementById(`view-${tab}`).classList.add('active');
    if (tab === 'library') loadLibrary();
}

async function searchWeb() {
    const searchInput = document.getElementById('searchInput');
    const query = searchInput.value;
    if (!query.trim()) return;

    const container = document.getElementById('searchResults');
    container.innerHTML = getSkeletonHTML(8);

    try {
        const res = await fetch(`/api/games/organize?name=${encodeURIComponent(query)}`);
        const data = await res.json();

        container.innerHTML = '';
        if (data.length === 0) {
            container.innerHTML = getEmptyStateHTML('search');
            return;
        }

        data.forEach(game => {
            const el = document.createElement('div');
            el.className = 'card';
            const gamePayload = {
                rawgId: game.rawgId,
                title: game.name,
                imageUrl: game.backgroundImage,
                genres: game.genres ? game.genres.join(', ') : 'Desconhecido'
            };
            const safeJson = JSON.stringify(gamePayload).replace(/"/g, '&quot;');
            const metaBadge = game.metacritic
                ? `<div class="meta-badge" title="Metacritic Score">${game.metacritic}</div>`
                : '';

            el.innerHTML = `
                <div class="thumb">
                    <div class="thumb-image" style="background-image: url('${game.backgroundImage || ''}')"></div>
                    ${metaBadge}
                </div>
                <div class="card-body">
                    <div class="game-title" title="${game.name}">${game.name}</div>
                    
                    <div class="game-meta">
                        ${game.releaseYear ? `<div class="meta-row"><span class="meta-label">Released:</span> <span class="meta-value">${game.releaseYear}</span></div>` : ''}
                        ${game.genres ? `<div class="meta-row"><span class="meta-value" style="font-size:11px; opacity:0.8;">${game.genres.slice(0,3).join(', ')}</span></div>` : ''}
                    </div>

                    <div class="card-footer">
                        <button class="btn-add" onclick="openAddModal(${safeJson})">Adicionar</button>
                    </div>
                </div>
            `;
            container.appendChild(el);
        });
    } catch (e) {
        console.error(e);
        showToast('Erro ao buscar jogos.', true);
    }
}

async function loadLibrary() {
    document.getElementById('activeGrid').innerHTML = getSkeletonHTML(3);
    document.getElementById('finishedGrid').innerHTML = getSkeletonHTML(2);

    try {
        const res = await fetch('/api/library');
        allMyGames = await res.json();
        populateGenreFilter();
        applyFilters();
    } catch (e) {
        console.error(e);
        showToast('Erro ao carregar biblioteca.', true);
    }
}

function populateGenreFilter() {
    const select = document.getElementById('filterGenre');
    const currentVal = select.value;
    const uniqueGenres = new Set();
    allMyGames.forEach(g => {
        if (g.genres) {
            g.genres.split(',').forEach(genre => uniqueGenres.add(genre.trim()));
        }
    });
    select.innerHTML = '<option value="ALL">Todos os Gêneros</option>';
    Array.from(uniqueGenres).sort().forEach(g => {
        if (g) {
            const opt = document.createElement('option');
            opt.value = g;
            opt.innerText = g;
            select.appendChild(opt);
        }
    });
    select.value = currentVal;
}

function applyFilters() {
    const status = document.getElementById('filterStatus').value;
    const genre = document.getElementById('filterGenre').value;
    const sort = document.getElementById('sortBy').value;

    let filtered = allMyGames.filter(g => {
        const matchStatus = status === 'ALL' || g.gameStatus === status;
        const matchGenre = genre === 'ALL' || (g.genres && g.genres.includes(genre));
        return matchStatus && matchGenre;
    });

    if (sort !== 'custom') {
        filtered.sort((a, b) => {
            if (sort === 'newest') return b.id - a.id;
            if (sort === 'rating_desc') return b.rating - a.rating;
            if (sort === 'rating_asc') return a.rating - b.rating;
            if (sort === 'name_asc') return a.title.localeCompare(b.title);
            return 0;
        });
    } else {
        filtered.sort((a, b) => {
            const orderA = (a.listOrder !== null && a.listOrder !== undefined) ? a.listOrder : 999999;
            const orderB = (b.listOrder !== null && b.listOrder !== undefined) ? b.listOrder : 999999;
            if (orderA !== orderB) {
                return orderA - orderB;
            }
            return b.id - a.id;
        });
    }

    renderLibrarySplit(filtered, sort === 'custom');
}

function renderLibrarySplit(list, enableDrag) {
    const activeGrid = document.getElementById('activeGrid');
    const finishedGrid = document.getElementById('finishedGrid');

    document.getElementById('libCount').innerText = list.length;
    activeGrid.innerHTML = '';
    finishedGrid.innerHTML = '';

    const activeGames = list.filter(g => ['BACKLOG', 'PLAYING'].includes(g.gameStatus));
    const finishedGames = list.filter(g => ['COMPLETED', 'DROPPED'].includes(g.gameStatus));

    if (activeGames.length === 0) {
        activeGrid.innerHTML = getEmptyStateHTML('active');
    } else {
        activeGames.forEach(game => activeGrid.appendChild(createGameCard(game)));
    }
    if (finishedGames.length === 0) {
        finishedGrid.innerHTML = getEmptyStateHTML('finished');
    } else {
        finishedGames.forEach(game => finishedGrid.appendChild(createGameCard(game)));
    }

    if (sortableInstance) {
        sortableInstance.destroy();
        sortableInstance = null;
    }

    if (enableDrag && activeGames.length > 0) {
        activeGrid.classList.add('sortable-grid');
        sortableInstance = new Sortable(activeGrid, {
            animation: 150,
            ghostClass: 'sortable-ghost',
            dragClass: 'sortable-drag',
            onEnd: function (evt) {
                const newOrderIds = Array.from(activeGrid.children).map(card => parseInt(card.getAttribute('data-id')));
                saveReorder(newOrderIds);
            }
        });
    } else {
        activeGrid.classList.remove('sortable-grid');
    }
}
async function saveReorder(ids) {
    ids.forEach((id, index) => {
        const game = allMyGames.find(g => g.id === id);
        if (game) {
            game.listOrder = index;
        }
    });
    try {
        await fetch('/api/library/reorder', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(ids)
        });
    } catch(e) {
        console.error("Falha ao salvar ordem", e);
        showToast("Erro ao salvar ordem", true);
    }
}

function createGameCard(game) {
    const el = document.createElement('div');
    el.className = 'card';
    el.setAttribute('data-id', game.id);

    const safeJson = JSON.stringify(game).replace(/"/g, '&quot;');
    const starBadge = game.rating > 0 ? `<div class="rating-badge">★ ${game.rating}</div>` : '';

    el.innerHTML = `
        <div class="thumb">
            <div class="thumb-image" style="background-image: url('${game.imageUrl || ''}')"></div>
            ${starBadge}
        </div>
        <div class="card-body">
            <div class="game-title" title="${game.title}">${game.title}</div>
            
            <div class="game-meta">
                <div class="meta-row">
                    <span class="badge st-${game.gameStatus}">${translateStatus(game.gameStatus)}</span>
                </div>
                ${game.genres ? `<div class="meta-row"><span class="meta-value" style="font-size:11px; opacity:0.8;">${game.genres}</span></div>` : ''}
            </div>

            <div class="card-footer">
                <button class="btn-icon" title="Editar" onclick="openEditModal(${safeJson})">✏️</button>
                <button class="btn-icon delete" title="Remover" onclick="deleteGame(${game.id})">✖</button>
            </div>
        </div>
    `;
    return el;
}

function openAddModal(apiGame) {
    currentGameData = apiGame;
    currentGameData.gameStatus = 'BACKLOG';
    currentGameData.rating = 0;
    document.getElementById('modalTitle').innerText = `Adicionar: ${apiGame.title}`;
    setupModal(apiGame.gameStatus, apiGame.rating);
}

function openEditModal(dbGame) {
    currentGameData = dbGame;
    document.getElementById('modalTitle').innerText = `Editar: ${dbGame.title}`;
    setupModal(dbGame.gameStatus, dbGame.rating);
}

function setupModal(status, rating) {
    document.getElementById('modalStatus').value = status || 'BACKLOG';
    currentRating = rating || 0;
    resetStars();
    document.getElementById('gameModal').classList.add('open');
}

function closeModal() {
    document.getElementById('gameModal').classList.remove('open');
    currentGameData = null;
}

async function saveGame() {
    if (!currentGameData) return;
    const status = document.getElementById('modalStatus').value;
    const payload = {
        rawgId: currentGameData.rawgId,
        title: currentGameData.title,
        imageUrl: currentGameData.imageUrl,
        genres: currentGameData.genres,
        gameStatus: status,
        rating: currentRating,
        listOrder: 0
    };
    try {
        const res = await fetch('/api/library', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (res.ok) {
            closeModal();
            showToast('Jogo salvo com sucesso!');
            if (document.getElementById('view-library').classList.contains('active')) {
                loadLibrary();
            }
        } else {
            throw new Error();
        }
    } catch (e) {
        showToast('Erro ao salvar jogo.', true);
    }
}

async function deleteGame(id) {
    if (!id || !confirm("Remover da biblioteca?")) return;
    try {
        const res = await fetch(`/api/library/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast('Jogo removido.');
            loadLibrary();
        } else throw new Error();
    } catch (e) {
        showToast('Erro ao remover.', true);
    }
}

function hoverRating(val) { fillStars(val); updateLabel(val); }
function resetStars() { fillStars(currentRating); updateLabel(currentRating); }
function setRating(val) { currentRating = val; fillStars(val); }

function fillStars(limit) {
    document.querySelectorAll('.star').forEach(s => {
        const v = parseInt(s.dataset.value);
        s.classList.toggle('filled', v <= limit);
    });
}

function updateLabel(val) {
    const labels = ["Sem nota", "Ruim", "Razoável", "Bom", "Muito Bom", "Obra Prima"];
    document.getElementById('ratingLabel').innerText = labels[val] || "Sem nota";
}

function showToast(msg, isError = false) {
    const t = document.getElementById('toast');
    document.getElementById('toastMsg').innerText = msg;
    document.getElementById('toastIcon').innerText = isError ? '❌' : '✅';
    t.className = `toast ${isError ? 'error' : 'success'}`;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 3000);
}

function translateStatus(st) {
    const map = { 'BACKLOG': 'Backlog', 'PLAYING': 'Jogando', 'COMPLETED': 'Zerado', 'DROPPED': 'Larguei' };
    return map[st] || st;
}

function getSkeletonHTML(count = 3) {
    let html = '';
    for (let i = 0; i < count; i++) {
        html += `
            <div class="card skeleton-card">
                <div class="skeleton skeleton-thumb"></div>
                <div class="card-body skeleton-body">
                    <div class="skeleton skeleton-line"></div>
                    <div class="skeleton skeleton-line short"></div>
                    <div class="card-footer skeleton-footer">
                         <div class="skeleton skeleton-btn"></div>
                    </div>
                </div>
            </div>
        `;
    }
    return html;
}

/** Gera o HTML do estado vazio com ícones SVG (Heroicons) **/
function getEmptyStateHTML(type) {
    let iconPath = '';
    let title = '';
    let message = '';

    switch(type) {
        case 'search':
            iconPath = '<path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607zM13.5 10.5h-6" />';
            title = 'Nenhum jogo encontrado';
            message = 'Tente buscar por outro nome.';
            break;
        case 'active':
            iconPath = '<path stroke-linecap="round" stroke-linejoin="round" d="M15.59 14.37a6 6 0 01-5.84 7.38v-4.8m5.84-2.58a14.98 14.98 0 006.16-12.12A14.98 14.98 0 009.631 8.41m5.96 5.96a14.926 14.926 0 01-5.841 2.58m-.119-8.54a6 6 0 00-7.381 5.84h4.8m2.581-5.84a14.927 14.927 0 00-2.58 5.84m2.699 2.7c-.103.021-.207.041-.311.06a15.09 15.09 0 01-2.448-2.448 14.9 14.9 0 01.06-.312m-2.24 2.39a4.493 4.493 0 00-1.757 4.306 4.493 4.493 0 004.306-1.758M16.5 9a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0z" />';
            title = 'Sua fila está vazia';
            message = 'Adicione jogos do Backlog para começar sua jornada.';
            break;
        case 'finished':
            iconPath = '<path stroke-linecap="round" stroke-linejoin="round" d="M16.5 18.75h-9m9 0a3 3 0 013 3h-15a3 3 0 013-3m9 0v-3.375c0-.621-.503-1.125-1.125-1.125h-.871M7.5 18.75v-3.375c0-.621.504-1.125 1.125-1.125h.872m5.007 0H9.497m5.007 0a7.454 7.454 0 01-.982-3.172M9.497 14.25a7.454 7.454 0 00.981-3.172M5.25 4.236c-.982.143-1.954.317-2.916.52A6.003 6.003 0 007.73 9.728M5.25 4.236V4.5c0 2.108.966 3.99 2.48 5.228M5.25 4.236V2.721C7.456 2.41 9.71 2.25 12 2.25c2.291 0 4.545.16 6.75.47v1.516M7.73 9.728a6.726 6.726 0 002.748 1.35m8.272-6.842V4.5c0 2.108-.966 3.99-2.48 5.228m2.48-5.228c.962-.203 1.934-.377 2.916-.52M12 2.25c.289 0 .575.005.859.014m-1.718 0c.284.009.57.014.859.014m0 0a6.004 6.004 0 015.141 8.832m-5.141-8.832a6.004 6.004 0 01-5.141 8.832M2.25 19.5h19.5" />';
            title = 'Nenhum histórico ainda';
            message = 'Jogos zerados ou largados aparecerão aqui.';
            break;
    }

    return `
        <div class="empty-state">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                ${iconPath}
            </svg>
            <h4>${title}</h4>
            <p>${message}</p>
        </div>
    `;
}