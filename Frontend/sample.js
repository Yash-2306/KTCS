
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('active');
                }
            });
        }, { threshold: 0.15 });

        document.querySelectorAll('.reveal, .reveal-left, .reveal-right')
                .forEach(el => observer.observe(el));

const images ={
    sports:[
        "Assets/images/Sports/Sports_1 (1).jpeg",
        "Assets/images/Sports/Sports_1 (2).jpeg",
        "Assets/images/Sports/Sports_1 (3).jpeg",
        "Assets/images/Sports/Sports_1 (4).jpeg",
        "Assets/images/Sports/Sports_1 (5).jpeg",
        "Assets/images/Sports/Sports_1 (6).jpeg",
        "Assets/images/Sports/Sports_1 (7).jpeg",
        "Assets/images/Sports/Sports_1 (8).jpeg",
        "Assets/images/Sports/Sports_1 (9).jpeg",
        "Assets/images/Sports/Sports_1 (10).jpeg",
        "Assets/images/Sports/Sports_1 (11).jpeg",
        "Assets/images/Sports/Sports_1 (12).jpeg",
        "Assets/images/Sports/Sports_1 (13).jpeg",
    ],
    events: [
        "Assets/images/Events/Events_ (1).jpeg",
        "Assets/images/Events/Events_ (2).jpeg",
        "Assets/images/Events/Events_ (3).jpeg",
        "Assets/images/Events/Events_ (4).jpeg",
        "Assets/images/Events/Events_ (5).jpeg",
        "Assets/images/Events/Events_ (6).jpeg",
        "Assets/images/Events/Events_ (7).jpeg",
        "Assets/images/Events/Events_ (8).jpeg",
        "Assets/images/Events/Events_ (9).jpeg",
        "Assets/images/Events/Events_ (10).jpeg",  
        "Assets/images/Events/Events_ (11).jpeg",
        "Assets/images/Events/Events_ (12).jpeg",
        "Assets/images/Events/Events_ (13).jpeg",
        "Assets/images/Events/Events_ (14).jpeg",
        "Assets/images/Events/Events_ (15).jpeg",
        "Assets/images/Events/Events_ (16).jpeg",
        "Assets/images/Events/Events_ (17).jpeg",
        "Assets/images/Events/Events_ (18).jpeg",
        "Assets/images/Events/Events_ (19).jpeg",
        "Assets/images/Events/Events_ (20).jpeg",
        "Assets/images/Events/Events_ (21).jpeg",
        "Assets/images/Events/Events_ (22).jpeg",
        "Assets/images/Events/Events_ (23).jpeg",
        "Assets/images/Events/Events_ (24).jpeg",
        "Assets/images/Events/Events_ (25).jpeg",
        "Assets/images/Events/Events_ (26).jpeg",
        "Assets/images/Events/Events_ (27).jpeg",
        "Assets/images/Events/Events_ (28).jpeg",
        "Assets/images/Events/Events_ (29).jpeg",
        "Assets/images/Events/Events_ (30).jpeg",
        "Assets/images/Events/Events_ (31).jpeg",
    ],
    
    academics: [
        "Assets/images/Academics/Academics_ (1).jpeg",
        "Assets/images/Academics/Academics_ (2).jpeg",
        "Assets/images/Academics/Academics_ (3).jpeg",
        "Assets/images/Academics/Academics_ (4).jpeg",
        "Assets/images/Academics/Academics_ (5).jpeg",
        "Assets/images/Academics/Academics_ (6).jpeg" ,
        "Assets/images/Academics/Academics_ (7).jpeg",
        "Assets/images/Academics/Academics_ (8).jpeg",
        "Assets/images/Academics/Academics_ (9).jpeg",
        "Assets/images/Academics/Academics_ (10).jpeg"
    ],

    festivals: [
        "Assets/images/Festivals/festivals_ (1).jpeg",
        "Assets/images/Festivals/festivals_ (2).jpeg",
        "Assets/images/Festivals/festivals_ (3).jpeg",
        "Assets/images/Festivals/festivals_ (4).jpeg",
        "Assets/images/Festivals/festivals_ (5).jpeg",
        "Assets/images/Festivals/festivals_ (6).jpeg",
        "Assets/images/Festivals/festivals_ (7).jpeg",
        "Assets/images/Festivals/festivals_ (8).jpeg",
        "Assets/images/Festivals/festivals_ (9).jpeg",
        "Assets/images/Festivals/festivals_ (10).jpeg",
        "Assets/images/Festivals/festivals_ (11).jpeg",
        "Assets/images/Festivals/festivals_ (12).jpeg",
        "Assets/images/Festivals/festivals_ (13).jpeg",
        "Assets/images/Festivals/festivals_ (14).jpeg",
        "Assets/images/Festivals/festivals_ (15).jpeg",
        "Assets/images/Festivals/festivals_ (16).jpeg",
        "Assets/images/Festivals/festivals_ (17).jpeg",
        "Assets/images/Festivals/festivals_ (18).jpeg",
        "Assets/images/Festivals/festivals_ (19).jpeg",
        "Assets/images/Festivals/festivals_ (20).jpeg",
        "Assets/images/Festivals/festivals_ (21).jpeg",
        "Assets/images/Festivals/festivals_ (22).jpeg",
        "Assets/images/Festivals/festivals_ (23).jpeg",
        "Assets/images/Festivals/festivals_ (24).jpeg",
    ]
}

function renderGallery(images, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return; // ← exits if container doesn't exist on this page

    images.forEach((src) => {
        container.innerHTML += `
            <div class="overflow-hidden rounded-xl">
                <img src="${src}"
                     class="w-full h-64 object-cover hover:scale-105 transition duration-300">
            </div>
        `;
    });
}

renderGallery(images.sports, "sports-gallery");
renderGallery(images.academics, "academics-gallery");
renderGallery(images.events, "events-gallery");
renderGallery(images.festivals, "festivals-gallery");



document.querySelectorAll('.accordion-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        const content = btn.closest('.accordion-item')
                           .querySelector('.accordion-content');
        content.classList.toggle('open');
        
        // toggle + to ×
        btn.querySelector('.plus-icon').textContent = 
    content.classList.contains('open') ? '×' : '+';
    });
});