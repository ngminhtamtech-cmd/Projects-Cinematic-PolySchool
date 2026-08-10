(function () {
    document.querySelectorAll('[data-stepper]').forEach((stepper) => {
        const current = Number(stepper.dataset.currentStep || 1);
        stepper.querySelectorAll('[data-step]').forEach((step) => {
            const index = Number(step.dataset.step || 0);
            step.classList.toggle('is-active', index === current);
            step.classList.toggle('is-done', index < current);
        });
    });
})();
