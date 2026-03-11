// API 配置
const API_BASE = '/api/v1/recommendations';

// DOM 元素引用
const elements = {
    queryInput: document.getElementById('query-input'),
    topKSlider: document.getElementById('topk-slider'),
    topKValue: document.getElementById('topk-value'),
    minPrice: document.getElementById('min-price'),
    maxPrice: document.getElementById('max-price'),
    categories: document.getElementById('categories'),
    brands: document.getElementById('brands'),
    inStock: document.getElementById('in-stock'),
    searchBtn: document.getElementById('search-btn'),
    toggleFilters: document.getElementById('toggle-filters'),
    filters: document.getElementById('filters'),
    loading: document.getElementById('loading'),
    error: document.getElementById('error'),
    resultsSection: document.getElementById('results-section'),
    resultsList: document.getElementById('results-list'),
    resultsMeta: document.getElementById('results-meta')
};

// 初始化事件监听
function init() {
    elements.topKSlider.addEventListener('input', (e) => {
        elements.topKValue.textContent = e.target.value;
    });

    elements.toggleFilters.addEventListener('click', () => {
        elements.filters.classList.toggle('hidden');
        const isHidden = elements.filters.classList.contains('hidden');
        elements.toggleFilters.textContent = isHidden ? '高级过滤 ▼' : '高级过滤 ▲';
    });

    elements.searchBtn.addEventListener('click', handleSearch);

    // 支持 Enter 键提交（Ctrl+Enter 或 Cmd+Enter）
    elements.queryInput.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
            handleSearch();
        }
    });
}

// 核心搜索函数
async function handleSearch() {
    const query = elements.queryInput.value.trim();

    if (!query) {
        showError('请输入查询内容');
        elements.queryInput.focus();
        return;
    }

    const requestData = buildRequest();

    showLoading(true);
    hideError();
    hideResults();

    try {
        const response = await fetch(API_BASE, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestData)
        });

        if (!response.ok) {
            const errorText = await response.text();
            console.error('API Error:', errorText);
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        displayResults(data);

    } catch (error) {
        console.error('Search error:', error);
        showError('获取推荐失败: ' + error.message + '\n\n请检查：\n1. 应用是否正常运行\n2. Elasticsearch 是否已启动\n3. API 配置是否正确');
    } finally {
        showLoading(false);
    }
}

// 构建请求数据
function buildRequest() {
    const request = {
        query: elements.queryInput.value.trim(),
        topK: parseInt(elements.topKSlider.value)
    };

    const filters = {};
    let hasFilters = false;

    // 价格区间
    if (elements.minPrice.value) {
        filters.minPrice = parseFloat(elements.minPrice.value);
        hasFilters = true;
    }
    if (elements.maxPrice.value) {
        filters.maxPrice = parseFloat(elements.maxPrice.value);
        hasFilters = true;
    }

    // 分类
    if (elements.categories.value.trim()) {
        filters.categories = elements.categories.value
            .split(',')
            .map(s => s.trim())
            .filter(Boolean);
        if (filters.categories.length > 0) {
            hasFilters = true;
        } else {
            delete filters.categories;
        }
    }

    // 品牌
    if (elements.brands.value.trim()) {
        filters.brands = elements.brands.value
            .split(',')
            .map(s => s.trim())
            .filter(Boolean);
        if (filters.brands.length > 0) {
            hasFilters = true;
        } else {
            delete filters.brands;
        }
    }

    // 是否有货
    if (elements.inStock.checked) {
        filters.inStock = true;
        hasFilters = true;
    }

    if (hasFilters) {
        request.filters = filters;
    }

    return request;
}

// 展示结果
function displayResults(data) {
    if (!data.recommendations || data.recommendations.length === 0) {
        showError('未找到相关推荐，请尝试调整查询条件');
        return;
    }

    // 显示元数据
    elements.resultsMeta.innerHTML = `
        找到 <strong>${data.totalResults}</strong> 个推荐商品
        | 处理时间: <strong>${data.processingTimeMs}ms</strong>
        | 模型: <strong>${data.modelUsed}</strong>
    `;

    // 生成商品卡片
    elements.resultsList.innerHTML = data.recommendations.map(item => createProductCard(item)).join('');

    // 显示结果区域
    elements.resultsSection.classList.remove('hidden');

    // 滚动到结果区域
    elements.resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// 创建商品卡片 HTML
function createProductCard(item) {
    const imageUrl = item.imageUrl || 'images/img_1.png';
    const price = item.price != null ? `¥${item.price.toFixed(2)}` : '价格面议';
    const category = item.category ? `<span class="category">${escapeHtml(item.category)}</span>` : '';
    const brand = item.brand ? `<span class="brand">${escapeHtml(item.brand)}</span>` : '';
    const reason = item.reason ? `<div class="reason">${escapeHtml(item.reason)}</div>` : '';
    const description = item.description ? `<p class="description">${escapeHtml(item.description)}</p>` : '';

    return `
        <div class="product-card">
            <img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(item.name)}" class="product-image" onerror="this.src='images/img_1.png'">
            <div class="product-info">
                <h3>${escapeHtml(item.name)}</h3>
                <div class="product-meta">
                    <span class="price">${price}</span>
                    ${category}
                    ${brand}
                </div>
                ${reason}
                ${description}
            </div>
        </div>
    `;
}

// HTML 转义防止 XSS
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// 显示/隐藏辅助函数
function showLoading(show) {
    elements.loading.classList.toggle('hidden', !show);
    elements.searchBtn.disabled = show;
    elements.searchBtn.textContent = show ? '搜索中...' : '🔍 获取智能推荐';
}

function showError(message) {
    elements.error.textContent = message;
    elements.error.classList.remove('hidden');
}

function hideError() {
    elements.error.classList.add('hidden');
}

function hideResults() {
    elements.resultsSection.classList.add('hidden');
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', init);
