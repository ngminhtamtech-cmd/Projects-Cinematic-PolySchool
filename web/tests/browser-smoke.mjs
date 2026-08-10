import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { chromium } from "playwright-core";

const javaBase = process.env.CINEBOOK_JAVA_BASE
  ?? "http://localhost:8080/Website-ban-ve-xem-phim";
const nextBase = process.env.CINEBOOK_NEXT_BASE ?? "http://localhost:3000";
const fixture = path.resolve("..", "target", "qr-browser-fixture.png");
const testPassword = process.env.CINEBOOK_TEST_PASSWORD;
const tooEarlyTicket = process.env.CINEBOOK_TOO_EARLY_TICKET;
const cancelTicket = process.env.CINEBOOK_CANCEL_TICKET;
const uploadDirectory = process.env.CINEBOOK_UPLOAD_DIR;
const errors = [];
const uploadedFilesToCleanup = [];
let signedTooEarlyQr = null;

if (!testPassword) {
  throw new Error("Set CINEBOOK_TEST_PASSWORD for the seeded role accounts.");
}
if (!tooEarlyTicket) {
  throw new Error("Set CINEBOOK_TOO_EARLY_TICKET after preparing its test-DB fixture.");
}
if (!cancelTicket) {
  throw new Error("Set CINEBOOK_CANCEL_TICKET after preparing its test-DB fixture.");
}
if (!uploadDirectory || !path.isAbsolute(uploadDirectory)) {
  throw new Error("Set CINEBOOK_UPLOAD_DIR to the absolute test Tomcat upload directory.");
}

function browserExecutable() {
  const candidates = [
    process.env.CINEBOOK_CHROME,
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  ].filter(Boolean);
  const found = candidates.find((candidate) => fs.existsSync(candidate));
  if (!found) throw new Error("Set CINEBOOK_CHROME to an installed Chrome/Edge executable.");
  return found;
}

async function monitoredPage(context, label) {
  const page = await context.newPage();
  page.on("console", (message) => {
    if (message.type() === "error") {
      const location = message.location();
      const source = location.url ? ` (${location.url}:${location.lineNumber + 1})` : "";
      errors.push(`${label} console: ${message.text()}${source}`);
    }
  });
  page.on("pageerror", (error) => errors.push(`${label} pageerror: ${error.message}`));
  page.on("response", (response) => {
    if (response.status() >= 400) {
      errors.push(`${label} HTTP ${response.status()}: ${response.url()}`);
    }
  });
  return page;
}

async function login(page, email) {
  await page.goto(`${javaBase}/login`, { waitUntil: "domcontentloaded" });
  const loginForm = page.locator('form[action$="/login"]');
  await loginForm.locator('#loginEmailInput').fill(email);
  await loginForm.locator('#loginPasswordInput').fill(testPassword);
  await Promise.all([
    page.waitForNavigation({ waitUntil: "domcontentloaded" }),
    loginForm.locator('button[type="submit"]').click(),
  ]);
  assert.notEqual(new URL(page.url()).pathname, "/Website-ban-ve-xem-phim/login");
}

async function verifySeatDesignerAccess(page, role) {
  let response = await page.goto(`${javaBase}/admin/rooms`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200, `${role} must access the scoped room list.`);
  const seatEditorLink = page.locator('a[href*="/admin/rooms/seats?roomId="]').first();
  const href = await seatEditorLink.getAttribute("href");
  assert.ok(href, `${role} must have at least one editable room in scope.`);

  response = await page.goto(new URL(href, javaBase).href, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200, `${role} must open the shared seat designer.`);
  assert.equal(await page.locator("#seatDesigner").count(), 1);
  assert.equal(await page.locator('#seatGrid[role="grid"]').count(), 1);
  assert.equal(await page.locator('script[src*="admin-seat-designer-core.js"]').count(), 1);
  assert.equal(await page.locator('script[src*="admin-seat-designer.js"]').count(), 1);
  assert.equal(await page.locator('link[href*="admin-seat-designer.css"]').count(), 1);
  assert.equal(await page.locator('script[src*="cinema-seat-3d.js"]').count(), 1);
  assert.equal(await page.locator('link[href*="cinema-seat-3d.css"]').count(), 1);
  assert.equal(await page.locator("#seatPreviewSaveButton").isEnabled(), false,
    "Saving stays locked until the server preview succeeds.");
  const seat3dButton = page.locator("#seatDesigner3dButton");
  assert.equal(await seat3dButton.isEnabled(), false);
  await page.locator('#seatGrid .seat-designer__cell[data-seat-key]').first().focus();
  assert.equal(await seat3dButton.isEnabled(), true,
    "Selecting a seat must activate its 3D view.");
  await seat3dButton.click();
  await page.locator("#cinebookSeat3d.is-open").waitFor({ state: "visible" });
  assert.equal(await page.locator(".cb-seat3d__chair.is-selected").count(), 1);
  assert.equal(await page.locator('.cb-seat3d__modes [data-view="overview"]').count(), 1);
  assert.equal(await page.locator('.cb-seat3d__modes [data-view="seat"]').count(), 1);
  assert.equal(await page.locator('.cb-seat3d__viewport[data-view="overview"]').count(), 1,
    "The 3D experience must open with the whole auditorium before the seat POV.");
  assert.equal(await page.locator("#cbSeat3dMapGrid .cb-seat3d__map-seat").count() > 0, true);
  const theatreLayout = await page.evaluate(() => {
    const shell = document.querySelector('.cb-seat3d__shell').getBoundingClientRect();
    const screen = document.querySelector('.cb-seat3d__screen-frame').getBoundingClientRect();
    const selected = document.querySelector('.cb-seat3d__chair.is-selected').getBoundingClientRect();
    return {
      shellInsideViewport: shell.left >= 0 && shell.right <= innerWidth
        && shell.top >= 0 && shell.bottom <= innerHeight,
      screenAboveSeat: screen.bottom < selected.top,
    };
  });
  assert.equal(theatreLayout.shellInsideViewport, true);
  assert.equal(theatreLayout.screenAboveSeat, true,
    "Overview chairs must start below the cinema screen, never over the trailer.");
  await page.locator('.cb-seat3d__modes [data-view="seat"]').click();
  assert.equal(await page.locator('.cb-seat3d__viewport[data-view="seat"]').count(), 1);
  await page.getByRole("button", { name: "Đóng xem ghế 3D" }).click();
  await page.locator("#cinebookSeat3d").waitFor({ state: "hidden" });

  await page.locator('.seat-designer__toolbar [data-action="preview"]').click();
  await page.locator("#seatPreviewModal.is-open").waitFor({ state: "visible" });
  const previewSurface = await page.locator("#seatPreviewModal .seat-designer__dialog").evaluate((element) => ({
    background: getComputedStyle(element).backgroundColor,
    color: getComputedStyle(element).color,
    opacity: getComputedStyle(element).opacity,
  }));
  assert.equal(previewSurface.background, "rgb(255, 255, 255)");
  assert.equal(previewSurface.opacity, "1");
  assert.notEqual(previewSurface.color, "rgba(0, 0, 0, 0)");
  await page.getByRole("button", { name: "Đóng xem trước" }).click();

  const layout = await page.evaluate(() => ({
    pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    track: Math.round(document.querySelector("#seatGrid .seat-designer__cell")
      ?.getBoundingClientRect().width ?? 0),
    face: Math.round(document.querySelector("#seatGrid .seat-designer__seat-face")
      ?.getBoundingClientRect().width ?? 0),
    inspector: Math.round(document.querySelector("#seatInspector")
      ?.getBoundingClientRect().width ?? 0),
    actionPosition: getComputedStyle(document.querySelector(".seat-designer__action-bar")).position,
  }));
  assert.equal(layout.pageOverflow, 0, `${role} seat designer must not overflow the dashboard.`);
  assert.equal(layout.track, 44);
  assert.equal(layout.face, 36);
  assert.equal(layout.inspector, 304);
  assert.equal(layout.actionPosition, "static");
}

async function decodeImage(page, bytes, contentType = "image/png") {
  const dataUrl = `data:${contentType};base64,${Buffer.from(bytes).toString("base64")}`;
  return page.evaluate(async (source) => {
    const image = new Image();
    image.src = source;
    await image.decode();
    const canvas = document.createElement("canvas");
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) throw new Error("Canvas 2D is unavailable.");
    context.drawImage(image, 0, 0);
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height);
    const result = window.jsQR(pixels.data, pixels.width, pixels.height);
    return result?.data ?? null;
  }, dataUrl);
}

async function decodeFixture(page) {
  assert.ok(fs.existsSync(fixture), `Missing ${fixture}; run Maven tests first.`);
  return decodeImage(page, fs.readFileSync(fixture));
}

const browser = await chromium.launch({
  executablePath: browserExecutable(),
  headless: true,
});

try {
  const publicContext = await browser.newContext();
  const publicPage = await monitoredPage(publicContext, "public-next");
  let response = await publicPage.goto(`${javaBase}/home`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);

  await publicPage.goto(nextBase, { waitUntil: "networkidle" });
  assert.equal(new URL(publicPage.url()).pathname, "/phim");
  assert.equal(await publicPage.getByText("To get started").count(), 0);
  response = await publicPage.goto(`${nextBase}/phim/1`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);

  await publicPage.goto(`${nextBase}/dat-ve`, { waitUntil: "networkidle" });
  await publicPage.getByRole("heading", { name: "Mã suất chiếu không hợp lệ" }).waitFor();
  await publicPage.goto(`${nextBase}/dat-ve?showtimeId=1`, { waitUntil: "networkidle" });
  await publicPage.getByRole("heading", { name: "Suất chiếu không còn nhận đặt vé" }).waitFor();
  await publicPage.goto(`${nextBase}/dat-ve?showtimeId=999999`, { waitUntil: "networkidle" });
  await publicPage.getByRole("heading", { name: "Không tìm thấy suất chiếu" }).waitFor();
  await publicPage.goto(`${nextBase}/dat-ve?showtimeId=3`, { waitUntil: "networkidle" });
  assert.ok(publicPage.url().startsWith(javaBase), publicPage.url());

  const imageSource = `${javaBase}/assets/img/default-film.jpg`;
  const imageUrl = `${nextBase}/_next/image?url=${encodeURIComponent(imageSource)}&w=640&q=75`;
  const imageResponse = await publicContext.request.get(imageUrl);
  assert.equal(imageResponse.status(), 200);
  assert.match(imageResponse.headers()["content-type"] ?? "", /^image\//);

  // The seeded film's real thumbnail must also be renderable.  A green image
  // optimizer smoke against the fallback asset alone would miss a broken
  // catalog URL (the fixture previously pointed at a missing film1.jpg).
  const filmResponse = await publicContext.request.get(`${javaBase}/api/v1/films/1`);
  assert.equal(filmResponse.status(), 200);
  const filmPayload = await filmResponse.json();
  const filmThumbnail = filmPayload?.data?.film?.thumbnail;
  assert.ok(filmThumbnail, "Seeded film must expose a thumbnail.");
  const filmImageResponse = await publicContext.request.get(`${javaBase}${filmThumbnail}`);
  assert.equal(filmImageResponse.status(), 200, `Broken seeded thumbnail: ${filmThumbnail}`);
  assert.match(filmImageResponse.headers()["content-type"] ?? "", /^image\//);
  await publicContext.close();

  const memberContext = await browser.newContext();
  await memberContext.route("https://www.youtube.com/**", (route) => route.fulfill({
    status: 200,
    contentType: "text/html",
    body: "<!doctype html><title>Trailer smoke</title>",
  }));
  const memberPage = await monitoredPage(memberContext, "member");
  await login(memberPage, "member_bronze@test.com");

  // booking-showtime-toggle: selection advances to step 2. Return through the
  // real stepper before exercising deselect -> reselect on the same button.
  response = await memberPage.goto(`${javaBase}/booking`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);
  const filmCard = memberPage.locator("#filmSelectGrid .film-select-card").first();
  assert.equal(await filmCard.count(), 1);
  await filmCard.click();
  const visibleShowtimeToggle = memberPage
    .locator(".showtime-btn:not(.is-disabled):visible")
    .first();
  assert.equal(await visibleShowtimeToggle.count(), 1);
  const showtimeToggle = await visibleShowtimeToggle.elementHandle();
  assert.ok(showtimeToggle);
  await visibleShowtimeToggle.click();
  assert.ok(await showtimeToggle.evaluate((element) => element.classList.contains("active")));
  assert.ok(await memberPage.locator('.booking-step[data-step-id="2"]').evaluate(
    (element) => element.classList.contains("active"),
  ));
  const publicSeat3dButton = memberPage.locator("#seatView3dButton");
  assert.equal(await publicSeat3dButton.isEnabled(), false);
  const publicSeat = memberPage
    .locator('#seatGridNew .seat-cell:not(.booked):not(.held):not(.maintenance-seat)')
    .first();
  await publicSeat.waitFor({ state: "visible" });
  await publicSeat.click();
  assert.equal(await publicSeat3dButton.isEnabled(), true);
  await publicSeat3dButton.click();
  await memberPage.locator("#cinebookSeat3d.is-open").waitFor({ state: "visible" });
  assert.equal(await memberPage.locator(".cb-seat3d__chair.is-selected").count(), 1);
  assert.equal(await memberPage.locator('.cb-seat3d__viewport[data-view="overview"]').count(), 1);
  assert.equal(await memberPage.locator(".cb-seat3d__film-field").isHidden(), true);
  await memberPage.getByRole("button", { name: "Đóng xem ghế 3D" }).click();
  await memberPage.locator("#cinebookSeat3d").waitFor({ state: "hidden" });
  await publicSeat.click();
  assert.equal(await publicSeat3dButton.isEnabled(), false);
  await memberPage.locator('[data-stepper] [data-step="1"]').click();
  await showtimeToggle.waitForElementState("visible");
  await showtimeToggle.click();
  assert.ok(!await showtimeToggle.evaluate((element) => element.classList.contains("active")));
  assert.ok(await memberPage.locator("#btnSideContinue").isDisabled());
  await showtimeToggle.click();
  assert.ok(await showtimeToggle.evaluate((element) => element.classList.contains("active")));
  assert.ok(await memberPage.locator('.booking-step[data-step-id="2"]').evaluate(
    (element) => element.classList.contains("active"),
  ));

  response = await memberPage.goto(`${javaBase}/booking?showtimeId=3`, {
    waitUntil: "networkidle",
  });
  assert.equal(response?.status(), 200);

  const productQrResponse = await memberContext.request.get(
    `${javaBase}/tickets/qr/${tooEarlyTicket}`,
  );
  assert.equal(productQrResponse.status(), 200);
  assert.match(productQrResponse.headers()["content-type"] ?? "", /^image\/png/);
  signedTooEarlyQr = await productQrResponse.body();

  response = await memberPage.goto(`${javaBase}/orders/history`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);
  let cancellableCard = memberPage.locator(".ticket-card").filter({ hasText: cancelTicket });
  assert.equal(await cancellableCard.count(), 1);
  const cancellableForm = cancellableCard.locator('form[action$="/cancel"]');
  assert.equal(await cancellableForm.count(), 1);
  assert.equal(await cancellableForm.locator('input[name="_csrf"]').count(), 1);
  const cancelButton = cancellableForm.getByRole("button", { name: "❌ Hủy vé" });
  assert.equal(await cancelButton.count(), 1);
  await cancelButton.click();
  const cancelModal = memberPage.locator("#customConfirmModal");
  await cancelModal.waitFor({ state: "visible" });
  await Promise.all([
    memberPage.waitForNavigation({ waitUntil: "domcontentloaded" }),
    cancelModal.locator("#customConfirmOkBtn").click(),
  ]);
  assert.ok(memberPage.url().endsWith("/orders/history"), memberPage.url());
  const successNotice = memberPage.locator("#successNoticeModal");
  assert.equal(await successNotice.count(), 1);
  await successNotice.locator("button").click();
  await successNotice.waitFor({ state: "detached" });
  const pastTicketsButton = memberPage.locator("#tabBtnPast");
  assert.equal(await pastTicketsButton.count(), 1);
  await pastTicketsButton.click();
  cancellableCard = memberPage.locator(".ticket-card").filter({ hasText: cancelTicket });
  assert.equal(await cancellableCard.count(), 1);
  assert.equal(await cancellableCard.getByText("Đã hủy", { exact: true }).count(), 1);
  assert.ok(await cancellableCard.isVisible());
  await memberContext.close();

  const staffContext = await browser.newContext();
  const staffPage = await monitoredPage(staffContext, "staff");
  await login(staffPage, "staff@test.com");
  response = await staffPage.goto(`${javaBase}/staff/checkin`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);
  assert.equal(
    await staffPage.locator("html").getAttribute("data-jsqr-ready"),
    "true",
  );
  assert.equal(await staffPage.evaluate(() => typeof window.jsQR), "function");
  assert.equal(await decodeFixture(staffPage), "CINEBOOK-QR-SMOKE");

  assert.ok(signedTooEarlyQr, "Member flow must fetch the real signed ticket QR.");
  const signedTicketPayload = await decodeImage(staffPage, signedTooEarlyQr);
  assert.ok(signedTicketPayload, "The real product QR must decode.");
  assert.notEqual(signedTicketPayload, tooEarlyTicket);
  assert.ok(signedTicketPayload.startsWith(`${tooEarlyTicket}.`));
  await staffPage.locator("#scannedTicketCode").evaluate(
    (element, value) => { element.value = value; },
    signedTicketPayload,
  );
  await Promise.all([
    staffPage.waitForNavigation({ waitUntil: "domcontentloaded" }),
    staffPage.locator("#scanForm").evaluate((form) => form.requestSubmit()),
  ]);
  assert.match(await staffPage.locator(".verdict-code").innerText(), /TOO_EARLY/);

  await staffPage.locator("#manualTicketCode").fill(tooEarlyTicket);
  await Promise.all([
    staffPage.waitForNavigation({ waitUntil: "domcontentloaded" }),
    staffPage.locator(".manual-entry button[type=submit]").click(),
  ]);
  assert.match(await staffPage.locator(".verdict-code").innerText(), /TOO_EARLY/);
  await staffContext.close();

  const managerContext = await browser.newContext();
  await managerContext.route("https://www.youtube.com/**", (route) => route.fulfill({
    status: 200,
    contentType: "text/html",
    body: "<!doctype html><title>Trailer smoke</title>",
  }));
  const managerPage = await monitoredPage(managerContext, "manager");
  await login(managerPage, "manager@test.com");
  await verifySeatDesignerAccess(managerPage, "Manager");
  await managerPage.goto(`${javaBase}/admin/dashboard`, { waitUntil: "networkidle" });
  assert.equal(await managerPage.locator('a[href$="/admin/promotions"]').count(), 0);
  const memberCard = managerPage.locator('a[href$="/admin/users"]');
  const memberCountBefore = Number(await memberCard.locator("strong").innerText());

  const unique = Date.now();
  const createdEmail = `manager-browser-${unique}@test.com`;
  await managerPage.goto(`${javaBase}/admin/users`, { waitUntil: "networkidle" });
  await managerPage.locator('input[name="username"]').fill(`manager_browser_${unique}`);
  await managerPage.locator('input[name="fullName"]').fill("Manager Browser Smoke");
  await managerPage.locator('input[name="email"]').fill(createdEmail);
  await managerPage.locator('input[name="password"]').fill("SmokePass!2026");
  await Promise.all([
    managerPage.waitForNavigation({ waitUntil: "domcontentloaded" }),
    managerPage.locator('form[action$="/admin/users"] button[type="submit"]').first().click(),
  ]);
  assert.equal(await managerPage.getByText(createdEmail, { exact: true }).count(), 1);
  await managerPage.goto(`${javaBase}/admin/dashboard`, { waitUntil: "networkidle" });
  const memberCountAfter = Number(
    await managerPage.locator('a[href$="/admin/users"] strong').innerText(),
  );
  assert.equal(memberCountAfter, memberCountBefore + 1);

  // Use the context request client for the one intentional 403. Keeping the
  // monitored page free of expected failures lets every browser console or
  // subresource 4xx remain a release-blocking regression.
  response = await managerContext.request.get(`${javaBase}/admin/promotions`);
  assert.equal(response?.status(), 403);
  await managerContext.close();

  const adminContext = await browser.newContext();
  await adminContext.route("https://www.youtube.com/**", (route) => route.fulfill({
    status: 200,
    contentType: "text/html",
    body: "<!doctype html><title>Trailer smoke</title>",
  }));
  const adminPage = await monitoredPage(adminContext, "admin");
  await login(adminPage, "admin@test.com");
  await verifySeatDesignerAccess(adminPage, "Admin");

  // Exercise the real multipart admin upload path. A fallback poster alone
  // cannot detect context-prefix duplication or a missing /uploads optimizer
  // allowlist.
  const uploadTitle = `BROWSER-UPLOAD-${Date.now()}`;
  const isoDate = (offsetDays) => {
    const date = new Date(Date.now() + offsetDays * 86_400_000);
    return date.toISOString().slice(0, 10);
  };
  await adminPage.goto(`${javaBase}/admin/films?action=create`, { waitUntil: "networkidle" });
  await adminPage.locator('input[name="title"]').fill(uploadTitle);
  await adminPage.locator('input[name="language"]').fill("Vietnamese");
  await adminPage.locator('input[name="categories"]').fill("Browser QA");
  await adminPage.locator('input[name="directors"]').fill("Browser QA Director");
  await adminPage.locator('input[name="actors"]').fill("Browser QA Actor");
  await adminPage.locator('input[name="durationMinutes"]').fill("90");
  await adminPage.locator('select[name="status"]').selectOption("showing");
  await adminPage.locator('input[name="releaseDate"]').fill(isoDate(0));
  await adminPage.locator('input[name="endDate"]').fill(isoDate(30));
  await adminPage.locator('input[name="trailerUrl"]').fill("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
  await adminPage.locator('textarea[name="description"]').fill("Browser upload bridge smoke.");
  await adminPage.locator("#cinemaMultiselectTrigger").click();
  const cinemaCheckbox = adminPage.locator('input[name="cinemaIds"][value="1"]');
  await cinemaCheckbox.check();
  assert.ok(await cinemaCheckbox.isChecked());
  await adminPage.locator('input[name="thumbnailFile"]').setInputFiles(fixture);
  await adminPage.locator('input[name="bannerFile"]').setInputFiles(fixture);
  const [saveNavigation] = await Promise.all([
    adminPage.waitForNavigation({ waitUntil: "domcontentloaded" }),
    adminPage.locator('form[action$="/admin/films"] button[type="submit"]').last().click(),
  ]);
  assert.equal(saveNavigation?.status(), 200);
  assert.equal(await adminPage.locator(".flash.error").count(), 0,
    "Film save must not re-render a server-side validation error.");
  assert.equal(await adminPage.locator(".flash.success").count(), 1,
    "Film save must finish through the success redirect.");

  const uploadedListResponse = await adminContext.request.get(
    `${javaBase}/api/v1/films?q=${encodeURIComponent(uploadTitle)}&size=20`,
  );
  assert.equal(uploadedListResponse.status(), 200);
  const uploadedList = await uploadedListResponse.json();
  const uploadedFilm = uploadedList?.data?.find((film) => film.title === uploadTitle);
  assert.ok(uploadedFilm, "Uploaded film must be visible through the public API.");
  assert.match(uploadedFilm.thumbnail ?? "", /^\/uploads\/[0-9a-f-]+\.png$/);
  assert.ok(!uploadedFilm.thumbnail.includes("/Website-ban-ve-xem-phim/Website-ban-ve-xem-phim/"));

  const resolvedUploadRoot = path.resolve(uploadDirectory);
  for (const mediaPath of [uploadedFilm.thumbnail, uploadedFilm.banner]) {
    assert.match(mediaPath ?? "", /^\/uploads\/[0-9a-f-]+\.png$/);
    const uploadedPath = path.resolve(resolvedUploadRoot, path.basename(mediaPath));
    assert.equal(path.dirname(uploadedPath), resolvedUploadRoot);
    assert.ok(
      fs.existsSync(uploadedPath),
      `Upload was not written to the guarded directory: ${uploadedPath}`,
    );
    uploadedFilesToCleanup.push(uploadedPath);
  }

  const uploadedSource = `${javaBase}${uploadedFilm.thumbnail}`;
  const uploadedDirectResponse = await adminContext.request.get(uploadedSource);
  assert.equal(uploadedDirectResponse.status(), 200);
  assert.match(uploadedDirectResponse.headers()["content-type"] ?? "", /^image\//);
  const uploadedOptimizerResponse = await adminContext.request.get(
    `${nextBase}/_next/image?url=${encodeURIComponent(uploadedSource)}&w=640&q=75`,
  );
  assert.equal(uploadedOptimizerResponse.status(), 200);
  assert.match(uploadedOptimizerResponse.headers()["content-type"] ?? "", /^image\//);

  response = await adminPage.goto(`${nextBase}/phim/${uploadedFilm.id}`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);
  await adminPage.getByRole("heading", { name: uploadTitle }).waitFor();
  response = await adminPage.goto(
    `${nextBase}/phim?q=${encodeURIComponent(uploadTitle)}`,
    { waitUntil: "networkidle" },
  );
  assert.equal(response?.status(), 200);
  await adminPage.getByText(uploadTitle, { exact: true }).first().waitFor();

  // Remove the test film through the same admin UI, then delete only the exact
  // uploaded basename from the test upload root in the outer finally block.
  await adminPage.goto(`${javaBase}/admin/films`, { waitUntil: "networkidle" });
  const uploadedRow = adminPage
    .locator(".film-management-table tbody tr")
    .filter({ hasText: uploadTitle });
  assert.equal(await uploadedRow.count(), 1);
  await uploadedRow.locator("[data-delete-film-id]").click();
  const deleteModal = adminPage.locator("#filmDeleteBackdrop");
  await deleteModal.locator("#filmDeleteSubmit").waitFor({ state: "visible" });
  await adminPage.waitForFunction(() => {
    const submit = document.querySelector("#filmDeleteSubmit");
    return submit instanceof HTMLButtonElement && !submit.disabled;
  });
  assert.equal(await deleteModal.locator("#filmDeleteSubmit").isEnabled(), true);
  await Promise.all([
    adminPage.waitForNavigation({ waitUntil: "domcontentloaded" }),
    deleteModal.locator("#filmDeleteSubmit").click(),
  ]);
  const deletedFilmResponse = await adminContext.request.get(
    `${javaBase}/api/v1/films/${uploadedFilm.id}`,
  );
  assert.equal(deletedFilmResponse.status(), 404);

  await adminPage.goto(`${javaBase}/admin/dashboard`, { waitUntil: "networkidle" });
  assert.ok(await adminPage.locator('a[href$="/admin/promotions"]').count());
  response = await adminPage.goto(`${javaBase}/admin/promotions`, { waitUntil: "networkidle" });
  assert.equal(response?.status(), 200);
  await adminContext.close();

  assert.deepEqual(errors, [], errors.join("\n"));
  console.log("Browser smoke passed: public, Next, member, staff, manager and admin.");
} finally {
  await browser.close();
  for (const uploadedPath of uploadedFilesToCleanup) {
    if (fs.existsSync(uploadedPath)) {
      fs.unlinkSync(uploadedPath);
      assert.ok(!fs.existsSync(uploadedPath));
    }
  }
}
