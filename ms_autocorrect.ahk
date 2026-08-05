#Requires AutoHotkey v2.0
#SingleInstance Force
; ============================================================================
;  ms_autocorrect.ahk — Interslavic house-style autocorrect for Windows
;
;  GENERATED FILE — do not edit by hand.
;  Source: Projects\INTERSLOVE\keyboards\build_autocorrect.py
;  Rules:  Projects\INTERSLOVE\HOUSE_STYLE.md
;
;  WHAT IT DOES
;    1. Rewrites extended-orthography words to standard as you finish typing them
;       (`sųt` -> `sut`, `dėnj` -> `denj`). Word-level, fires on the word-ending
;       character — never mid-word, so it cannot mangle a longer word.
;    2. `;`-prefixed shortcuts for the four standard letters a Polish keyboard
;       lacks, for when AltGr is inconvenient.
;
;  WHAT IT DOES NOT DO
;    No suggestions, no swipe, no dictionary lookup — Windows has no user-extensible
;    prediction engine. This is the closest desktop analogue to the Android setup.
;
;  PRIMARY INPUT PATH IS STILL THE KEYBOARD LAYOUT
;    "Medžuslovjansky (latinica)" is installed: AltGr+C=c-caron, AltGr+S, AltGr+Z,
;    AltGr+E=e-caron (plus the whole extended block). This script is a safety net on
;    top of it, not a replacement.
;
;  Ctrl+Alt+M  toggles the autocorrect off/on (the shortcuts keep working).
; ============================================================================

TraySetIcon("shell32.dll", 45)
A_IconTip := "Interslavic house-style autocorrect"

global MSCorrect := true

^!m:: {
    global MSCorrect
    MSCorrect := !MSCorrect
    Suspend(!MSCorrect)
    TrayTip("Interslavic autocorrect", MSCorrect ? "ON" : "OFF", 1)
}


; --- typing aids: standard letters without AltGr ---
:*C:;c::č
:*C:;s::š
:*C:;z::ž
:*C:;e::ě
:*C:;C::Č
:*C:;S::Š
:*C:;Z::Ž
:*C:;E::Ě
:*C:;dz::dž
:*C:;Dz::Dž

; --- house-style corrections: extended -> standard (word-level) ---
; 118 pairs, from the 2026-08-05 vault sweep
::najčęstějših::najčestějših
::prěnoćevati::prěnočevati
::gostinnosť::gostinnost
::klaviaturų::klaviaturu
::odpuščeńje::odpuščenje
::slovjańsky::slovjanjsky
::trpělivosť::trpelivost
::tŕpělivosť::trpelivost
::budućnosť::budučnost
::izvråćeny::izvračeny
::probuđeny::probudženy
::šťestliva::ščestliva
::šťestlivy::ščestlivy
::hviljejų::hviljeju
::krėstovy::krestovy
::nastupnų::nastupnu
::nasyćeny::nasyčeny
::noćevati::nočevati
::radostjų::radostju
::råzumnik::razumnik
::ščedrosť::ščedrost
::bråniti::braniti
::gordosť::gordost
::grėměti::greměti
::mȯlčati::molčati
::plęsati::plesati
::predviď::prědvidi
::råzsvět::razsvět
::slabosť::slabost
::vraćati::vračati
::zvųčati::zvučati
::ćuđinėc::čudžinec
::šťestje::ščestje
::braniť::braniti
::brȯnja::bronja
::glåsno::glasno
::hlåpėc::hlapec
::jedinų::jedinu
::krųgom::krugom
::lisťje::listje
::ljubȯv::ljubov
::malosť::malost
::polnoć::polnoč
::prědȯk::prědok
::prěveď::prěvedi
::pěsnjų::pěsnju
::radosť::radost
::rųkami::rukami
::sȯlnce::solnce
::zabųdi::zabudi
::žalosť::žalost
::bijųt::bijut
::bitvų::bitvu
::bųben::buben
::bųbėn::buben
::desęť::deset
::dȯlgo::dolgo
::dȯlgy::dolgy
::goręt::goret
::gųsli::gusli
::gȯrdy::gordy
::krėst::krest
::krųgu::krugu
::kȯgda::kogda
::kȯgdy::kogdy
::mlådy::mlady
::měsęc::měsec
::nočjų::nočju
::pamęt::pamet
::pamęť::pamet
::råzum::razum
::rųkah::rukah
::siľny::siljny
::svojų::svoju
::sŕdca::srdca
::sŕdce::srdce
::sŕdcu::srdcu
::sųsěd::susěd
::tanėc::tanec
::tvojų::tvoju
::tȯjže::tojže
::vitęź::vitez
::vzęti::vzeti
::znamę::zname
::zvųči::zvuči
::brať::brati
::dneś::dnes
::dnėś::dnes
::dėnj::denj
::dȯlg::dolg
::glås::glas
::gosť::gost
::gråd::grad
::idųt::idut
::krųg::krug
::mojų::moju
::nogų::nogu
::rěkų::rěku
::rųka::ruka
::rųky::ruky
::stęg::steg
::tųga::tuga
::vodų::vodu
::vęče::veče
::ćuđi::čudži
::črnų::črnu
::čuže::čudže
::imę::ime
::męč::meč
::mųž::muž
::noć::noč
::piť::piti
::pųt::put
::pųť::put
::sųt::sut
::sȯn::son
::tȯj::toj
::sę::se
